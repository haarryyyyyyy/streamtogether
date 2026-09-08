/**
 * WatchTogether — Production Server-Authoritative WebSocket & HTTP Synchronization Server
 * Features:
 * - Real-time server-authoritative playback synchronization with drift correction
 * - High-precision NTP-style 4-timestamp clock synchronization
 * - Frictionless guest identity validation (guestId + displayName)
 * - Room control modes: HOST_ONLY vs SHARED
 * - Full host management: kick participant, lock/unlock room, media switcher
 * - Real-time text chat with rate limiting and anti-spam
 * - Participant presence and real-time buffering state monitoring
 * - REST endpoints for health check, room status validation, and sample legal media catalog
 */

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');
const fs = require('fs');
const path = require('path');
const os = require('os');

const PORT = parseInt(process.env.PORT, 10) || 8080;
const HOST = process.env.HOST || '0.0.0.0';

// Uploads storage for local video stream relay
const UPLOADS_DIR = path.join(os.tmpdir(), 'watchtogether_streams');
if (!fs.existsSync(UPLOADS_DIR)) {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true });
}

// Map: roomCode -> { filePath, fileName, mimeType, size, createdAt }
const roomStreams = new Map();

// In-Memory Room Store
// Room State Schema:
// {
//   code: string,                    // e.g. "WATCH-8K42"
//   hostId: string,                  // guestId of host
//   hostName: string,
//   mediaUrl: string,
//   mediaTitle: string,
//   isPlaying: boolean,
//   positionSec: number,
//   anchorServerTime: number,        // Server timestamp when playback state changed
//   controlMode: 'HOST_ONLY' | 'SHARED',
//   isLocked: boolean,
//   createdAt: number,
//   peers: Map<string, Peer>,        // guestId -> Peer
//   chatHistory: Array<ChatMessage>
// }
const rooms = new Map();

// Sample curated public domain & open-license video catalog
const SAMPLE_MEDIA_CATALOG = [
  {
    id: 'big-buck-bunny',
    title: 'Big Buck Bunny (4K/60fps Open Movie)',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    thumbnail: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg',
    durationSec: 596,
    license: 'Creative Commons Attribution 3.0'
  },
  {
    id: 'tears-of-steel',
    title: 'Tears of Steel (Blender VFX Sci-Fi)',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
    thumbnail: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg',
    durationSec: 734,
    license: 'Creative Commons Attribution 3.0'
  },
  {
    id: 'sintel',
    title: 'Sintel (Blender Animation Studio)',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4',
    thumbnail: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg',
    durationSec: 888,
    license: 'Creative Commons Attribution 3.0'
  },
  {
    id: 'elephants-dream',
    title: "Elephants Dream (Open Movie Project)",
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
    thumbnail: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg',
    durationSec: 654,
    license: 'Creative Commons Attribution 2.5'
  },
  {
    id: 'for-bigger-blazes',
    title: 'For Bigger Blazes (Chromecast Demo 1080p)',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4',
    thumbnail: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg',
    durationSec: 15,
    license: 'Google Open Media'
  }
];

// Generate 6-digit numeric room codes like "849201"
function generateRoomCode() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

// Compute exact current playback position based on server clock
function calculateCurrentPosition(room) {
  if (!room.isPlaying) {
    return Math.max(0, room.positionSec);
  }
  const now = Date.now();
  const elapsedSec = (now - room.anchorServerTime) / 1000.0;
  return Math.max(0, room.positionSec + elapsedSec);
}

// Format participant list for clients
function getParticipantList(room) {
  const list = [];
  room.peers.forEach((peer, guestId) => {
    list.push({
      guestId: guestId,
      displayName: peer.displayName,
      isHost: guestId === room.hostId,
      isBuffering: peer.isBuffering || false,
      isOnline: peer.ws.readyState === WebSocket.OPEN,
      joinedAt: peer.joinedAt
    });
  });
  return list;
}

// Safe JSON send
function sendJson(ws, obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    try {
      ws.send(JSON.stringify(obj));
    } catch (err) {
      console.error('[Send Error]', err.message);
    }
  }
}

// Broadcast message to all peers in a room
function broadcastToRoom(roomCode, message, excludeWs = null) {
  const room = rooms.get(roomCode);
  if (!room) return;

  const payload = JSON.stringify(message);
  room.peers.forEach((peer) => {
    if (peer.ws !== excludeWs && peer.ws.readyState === WebSocket.OPEN) {
      try {
        peer.ws.send(payload);
      } catch (err) {
        console.error(`[Broadcast Error to ${peer.guestId}]`, err.message);
      }
    }
  });
}

// Setup WebSocket Server Handlers
function setupWebSocketServer(wss) {
  wss.on('connection', (ws, req) => {
    let currentRoomCode = null;
    let currentGuestId = null;
    let currentDisplayName = 'Guest';
    let lastChatTimestamp = 0;

    // Heartbeat check
    ws.isAlive = true;
    ws.on('pong', () => {
      ws.isAlive = true;
    });

    ws.on('message', (rawMessage) => {
      try {
        const data = JSON.parse(rawMessage.toString());
        const now = Date.now();
        const type = data.type || data.action;

        switch (type) {
          // ==========================================
          // 1. HIGH-PRECISION NTP CLOCK SYNCHRONIZATION
          // ==========================================
          case 'NTP_PING':
          case 'client:ping': {
            sendJson(ws, {
              type: 'NTP_PONG',
              clientTime: data.clientTime || data.t1,
              serverTime: now
            });
            break;
          }

          // ==========================================
          // 2. ROOM CREATION
          // ==========================================
          case 'CREATE_ROOM':
          case 'room:create': {
            currentGuestId = data.guestId || `gst_${Math.random().toString(36).substring(2, 10)}`;
            currentDisplayName = (data.displayName || data.username || 'Host').trim();
            
            let code = generateRoomCode();
            while (rooms.has(code)) {
              code = generateRoomCode();
            }

            const initialMedia = data.mediaUrl || '';
            const initialTitle = data.mediaTitle || 'Movie Stream';

            const room = {
              code: code,
              hostId: currentGuestId,
              hostName: currentDisplayName,
              mediaUrl: initialMedia,
              mediaTitle: initialTitle,
              isPlaying: false,
              positionSec: 0,
              anchorServerTime: now,
              controlMode: 'HOST_ONLY',
              isLocked: false,
              createdAt: now,
              peers: new Map(),
              chatHistory: []
            };

            currentRoomCode = code;
            room.peers.set(currentGuestId, {
              guestId: currentGuestId,
              displayName: currentDisplayName,
              ws: ws,
              isBuffering: false,
              joinedAt: now
            });

            rooms.set(code, room);
            console.log(`[Room Created] Code: ${code} | Host: ${currentDisplayName} (${currentGuestId})`);

            // Respond with ROOM_CREATED / room:state
            sendJson(ws, {
              type: 'ROOM_CREATED',
              roomCode: code,
              isHost: true,
              guestId: currentGuestId,
              roomState: {
                code: room.code,
                hostId: room.hostId,
                hostName: room.hostName,
                mediaUrl: room.mediaUrl,
                mediaTitle: room.mediaTitle,
                isPlaying: room.isPlaying,
                positionSec: room.positionSec,
                anchorServerTime: room.anchorServerTime,
                controlMode: room.controlMode,
                isLocked: room.isLocked,
                participants: getParticipantList(room)
              }
            });
            break;
          }

          // ==========================================
          // 3. ROOM JOINING
          // ==========================================
          case 'JOIN_ROOM':
          case 'room:join': {
            const rawCode = (data.roomCode || data.pin || '').toUpperCase().trim();
            currentGuestId = data.guestId || `gst_${Math.random().toString(36).substring(2, 10)}`;
            currentDisplayName = (data.displayName || data.username || 'Guest').trim();

            const room = rooms.get(rawCode);
            if (!room) {
              sendJson(ws, {
                type: 'ERROR',
                code: 'ROOM_NOT_FOUND',
                message: "This room doesn't exist. Check the code and try again."
              });
              break;
            }

            if (room.isLocked) {
              sendJson(ws, {
                type: 'ERROR',
                code: 'ROOM_LOCKED',
                message: 'This room is currently locked by the host.'
              });
              break;
            }

            currentRoomCode = rawCode;
            const isHost = currentGuestId === room.hostId;

            // Save / Update peer in room
            room.peers.set(currentGuestId, {
              guestId: currentGuestId,
              displayName: currentDisplayName,
              ws: ws,
              isBuffering: false,
              joinedAt: now
            });

            const currentPos = calculateCurrentPosition(room);
            console.log(`[User Joined] ${currentDisplayName} (${currentGuestId}) joined ${rawCode} | Current Pos: ${currentPos.toFixed(2)}s`);

            // Send authoritative state to the joining user
            sendJson(ws, {
              type: 'ROOM_JOINED',
              roomCode: room.code,
              isHost: isHost,
              guestId: currentGuestId,
              roomState: {
                code: room.code,
                hostId: room.hostId,
                hostName: room.hostName,
                mediaUrl: room.mediaUrl,
                mediaTitle: room.mediaTitle,
                isPlaying: room.isPlaying,
                positionSec: currentPos,
                anchorServerTime: now,
                controlMode: room.controlMode,
                isLocked: room.isLocked,
                participants: getParticipantList(room)
              },
              chatHistory: room.chatHistory.slice(-50)
            });

            // Broadcast join notification to room peers
            broadcastToRoom(rawCode, {
              type: 'PARTICIPANT_JOINED',
              guestId: currentGuestId,
              displayName: currentDisplayName,
              isHost: isHost,
              participants: getParticipantList(room)
            }, ws);

            // Add system chat message
            const sysMsg = {
              id: `sys_${now}_${Math.random().toString(36).substring(2, 6)}`,
              senderId: 'SYSTEM',
              senderName: 'System',
              isSystem: true,
              isHost: false,
              text: `${currentDisplayName} joined the room`,
              timestamp: now
            };
            room.chatHistory.push(sysMsg);
            broadcastToRoom(rawCode, {
              type: 'CHAT_MESSAGE',
              message: sysMsg
            });
            break;
          }

          // ==========================================
          // 4. PLAYBACK CONTROLS (PLAY / PAUSE / SEEK)
          // ==========================================
          case 'ACTION_PLAY':
          case 'playback:play': {
            const room = rooms.get(currentRoomCode);
            if (!room) break;

            if (room.controlMode === 'HOST_ONLY' && currentGuestId !== room.hostId) {
              sendJson(ws, {
                type: 'ERROR',
                code: 'PERMISSION_DENIED',
                message: 'Only the host can control playback in Host Control Mode.'
              });
              break;
            }

            const requestedPos = data.positionSec !== undefined 
              ? Math.max(0, data.positionSec) 
              : calculateCurrentPosition(room);

            room.isPlaying = true;
            room.positionSec = requestedPos;
            room.anchorServerTime = now;

            console.log(`[PLAY] Room ${currentRoomCode} at ${requestedPos.toFixed(2)}s by ${currentDisplayName}`);

            broadcastToRoom(currentRoomCode, {
              type: 'SYNC_STATE',
              isPlaying: true,
              positionSec: requestedPos,
              anchorServerTime: now,
              senderId: currentGuestId,
              senderName: currentDisplayName,
              action: 'PLAY'
            });
            break;
          }

          case 'ACTION_PAUSE':
          case 'playback:pause': {
            const room = rooms.get(currentRoomCode);
            if (!room) break;

            if (room.controlMode === 'HOST_ONLY' && currentGuestId !== room.hostId) {
              sendJson(ws, {
                type: 'ERROR',
                code: 'PERMISSION_DENIED',
                message: 'Only the host can control playback in Host Control Mode.'
              });
              break;
            }

            const currentPos = data.positionSec !== undefined 
              ? Math.max(0, data.positionSec) 
              : calculateCurrentPosition(room);

            room.isPlaying = false;
            room.positionSec = currentPos;
            room.anchorServerTime = now;

            console.log(`[PAUSE] Room ${currentRoomCode} at ${currentPos.toFixed(2)}s by ${currentDisplayName}`);

            broadcastToRoom(currentRoomCode, {
              type: 'SYNC_STATE',
              isPlaying: false,
              positionSec: currentPos,
              anchorServerTime: now,
              senderId: currentGuestId,
              senderName: currentDisplayName,
              action: 'PAUSE'
            });
            break;
          }

          case 'ACTION_SEEK':
          case 'playback:seek': {
            const room = rooms.get(currentRoomCode);
            if (!room) break;

            if (room.controlMode === 'HOST_ONLY' && currentGuestId !== room.hostId) {
              sendJson(ws, {
                type: 'ERROR',
                code: 'PERMISSION_DENIED',
                message: 'Only the host can seek in Host Control Mode.'
              });
              break;
            }

            const seekPos = Math.max(0, data.positionSec || 0);
            room.positionSec = seekPos;
            room.anchorServerTime = now;

            console.log(`[SEEK] Room ${currentRoomCode} to ${seekPos.toFixed(2)}s by ${currentDisplayName}`);

            broadcastToRoom(currentRoomCode, {
              type: 'SYNC_STATE',
              isPlaying: room.isPlaying,
              positionSec: seekPos,
              anchorServerTime: now,
              senderId: currentGuestId,
              senderName: currentDisplayName,
              action: 'SEEK'
            });
            break;
          }

          // ==========================================
          // 5. CHANGE MEDIA (HOST ONLY)
          // ==========================================
          case 'CHANGE_MEDIA':
          case 'playback:media_change': {
            const room = rooms.get(currentRoomCode);
            if (!room) break;

            if (currentGuestId !== room.hostId) {
              sendJson(ws, {
                type: 'ERROR',
                code: 'PERMISSION_DENIED',
                message: 'Only the room host can change the media.'
              });
              break;
            }

            const newUrl = data.mediaUrl || '';
            const newTitle = data.mediaTitle || 'Custom Video';

            if (!newUrl) {
              sendJson(ws, { type: 'ERROR', message: 'Media URL cannot be empty.' });
              break;
            }

            room.mediaUrl = newUrl;
            room.mediaTitle = newTitle;
            room.isPlaying = false;
            room.positionSec = 0;
            room.anchorServerTime = now;

            console.log(`[MEDIA CHANGED] Room ${currentRoomCode}: ${newTitle} (${newUrl})`);

            broadcastToRoom(currentRoomCode, {
              type: 'MEDIA_CHANGED',
              mediaUrl: room.mediaUrl,
              mediaTitle: room.mediaTitle,
              positionSec: 0,
              anchorServerTime: now,
              senderName: currentDisplayName
            });

            // Announce in chat
            const mediaMsg = {
              id: `sys_${now}`,
              senderId: 'SYSTEM',
              senderName: 'System',
              isSystem: true,
              isHost: false,
              text: `Host changed media to "${newTitle}"`,
              timestamp: now
            };
            room.chatHistory.push(mediaMsg);
            broadcastToRoom(currentRoomCode, { type: 'CHAT_MESSAGE', message: mediaMsg });
            break;
          }

          // ==========================================
          // 6. BUFFERING TELEMETRY
          // ==========================================
          case 'BUFFERING_STATE':
          case 'playback:buffering': {
            const room = rooms.get(currentRoomCode);
            if (!room) break;

            const peer = room.peers.get(currentGuestId);
            if (peer) {
              peer.isBuffering = !!data.isBuffering;
            }

            broadcastToRoom(currentRoomCode, {
              type: 'PARTICIPANT_BUFFERING',
              guestId: currentGuestId,
              isBuffering: !!data.isBuffering,
              participants: getParticipantList(room)
            }, ws);
            break;
          }

          // ==========================================
          // 7. HOST MANAGEMENT CONTROLS
          // ==========================================
          case 'SET_CONTROL_MODE':
          case 'room:control_mode': {
            const room = rooms.get(currentRoomCode);
            if (!room || currentGuestId !== room.hostId) break;

            const mode = data.mode === 'SHARED' ? 'SHARED' : 'HOST_ONLY';
            room.controlMode = mode;

            console.log(`[CONTROL MODE] Room ${currentRoomCode} set to ${mode}`);

            broadcastToRoom(currentRoomCode, {
              type: 'CONTROL_MODE_CHANGED',
              controlMode: mode
            });
            break;
          }

          case 'SET_ROOM_LOCK':
          case 'room:lock': {
            const room = rooms.get(currentRoomCode);
            if (!room || currentGuestId !== room.hostId) break;

            room.isLocked = !!data.isLocked;
            console.log(`[ROOM LOCK] Room ${currentRoomCode} locked: ${room.isLocked}`);

            broadcastToRoom(currentRoomCode, {
              type: 'ROOM_LOCK_CHANGED',
              isLocked: room.isLocked
            });
            break;
          }

          case 'KICK_PARTICIPANT':
          case 'room:kick': {
            const room = rooms.get(currentRoomCode);
            if (!room || currentGuestId !== room.hostId) break;

            const targetGuestId = data.targetGuestId;
            const targetPeer = room.peers.get(targetGuestId);

            if (targetPeer && targetGuestId !== room.hostId) {
              sendJson(targetPeer.ws, {
                type: 'KICKED_FROM_ROOM',
                message: 'You have been removed from the room by the host.'
              });
              try {
                targetPeer.ws.close();
              } catch (e) {}
              room.peers.delete(targetGuestId);

              broadcastToRoom(currentRoomCode, {
                type: 'PARTICIPANT_LEFT',
                guestId: targetGuestId,
                displayName: targetPeer.displayName,
                participants: getParticipantList(room)
              });
            }
            break;
          }

          // ==========================================
          // 8. TEXT CHAT (Instant, Clean, No spam)
          // ==========================================
          case 'SEND_CHAT':
          case 'chat:send': {
            if (!currentRoomCode) break;
            const room = rooms.get(currentRoomCode);
            if (!room) break;

            // Rate limit: 200ms per message
            if (now - lastChatTimestamp < 200) {
              break;
            }
            lastChatTimestamp = now;

            const rawText = (data.text || '').trim();
            if (!rawText || rawText.length > 500) break; // Max 500 chars

            const isHost = currentGuestId === room.hostId;
            const chatMsg = {
              id: `msg_${now}_${Math.random().toString(36).substring(2, 7)}`,
              senderId: currentGuestId,
              senderName: currentDisplayName,
              isHost: isHost,
              isSystem: false,
              text: rawText,
              timestamp: now
            };

            // Store in history (max 100)
            room.chatHistory.push(chatMsg);
            if (room.chatHistory.length > 100) {
              room.chatHistory.shift();
            }

            broadcastToRoom(currentRoomCode, {
              type: 'CHAT_MESSAGE',
              message: chatMsg
            });
            break;
          }

          default:
            console.log(`[WS] Unhandled message type: ${type}`);
        }
      } catch (err) {
        console.error('[WS Parse Error]', err.message);
      }
    });

    // Handle Client Disconnect
    ws.on('close', () => {
      console.log(`[WS Disconnect] ${currentDisplayName} (${currentGuestId})`);
      if (currentRoomCode && rooms.has(currentRoomCode)) {
        const room = rooms.get(currentRoomCode);
        room.peers.delete(currentGuestId);

        if (room.peers.size === 0) {
          console.log(`[Room Cleaned] Room ${currentRoomCode} is empty. Removed.`);
          // Clean up any uploaded stream files
          const streamInfo = roomStreams.get(currentRoomCode);
          if (streamInfo && streamInfo.filePath && fs.existsSync(streamInfo.filePath)) {
            try {
              fs.unlinkSync(streamInfo.filePath);
              console.log(`[Stream Cleaned] Deleted stream file for room ${currentRoomCode}`);
            } catch (e) {
              console.error(`[Stream Cleanup Error]`, e.message);
            }
          }
          roomStreams.delete(currentRoomCode);
          rooms.delete(currentRoomCode);
        } else {
          // If host left, assign next senior peer as host
          if (room.hostId === currentGuestId) {
            const nextHostId = room.peers.keys().next().value;
            const nextHost = room.peers.get(nextHostId);
            room.hostId = nextHostId;
            room.hostName = nextHost ? nextHost.displayName : 'Host';

            console.log(`[Host Transferred] Room ${currentRoomCode} -> New Host: ${room.hostName} (${nextHostId})`);

            broadcastToRoom(currentRoomCode, {
              type: 'HOST_CHANGED',
              newHostId: nextHostId,
              newHostName: room.hostName,
              participants: getParticipantList(room)
            });
          }

          // Notify room of departure
          broadcastToRoom(currentRoomCode, {
            type: 'PARTICIPANT_LEFT',
            guestId: currentGuestId,
            displayName: currentDisplayName,
            participants: getParticipantList(room)
          });
        }
      }
    });
  });

  // Heartbeat ping interval every 30s to keep connection alive
  setInterval(() => {
    wss.clients.forEach((ws) => {
      if (ws.isAlive === false) return ws.terminate();
      ws.isAlive = false;
      ws.ping();
    });
  }, 30000);
}

// REST API & HTTP Server
const server = http.createServer((req, res) => {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  // Health Check
  if (parsedUrl.pathname === '/health' || parsedUrl.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'healthy',
      app: 'WatchTogether Backend',
      version: '1.0.0',
      activeRooms: rooms.size,
      uptimeSec: Math.floor(process.uptime()),
      serverTime: Date.now()
    }));
    return;
  }

  // Curated Sample Media Catalog
  if (parsedUrl.pathname === '/api/v1/media/samples') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'success',
      catalog: SAMPLE_MEDIA_CATALOG
    }));
    return;
  }

  // Room Existence & Status Check
  if (parsedUrl.pathname.startsWith('/api/v1/rooms/check/')) {
    const code = parsedUrl.pathname.replace('/api/v1/rooms/check/', '').toUpperCase().trim();
    const room = rooms.get(code);

    if (!room) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        exists: false,
        message: "This room doesn't exist. Check the code and try again."
      }));
      return;
    }

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      exists: true,
      roomCode: room.code,
      hostName: room.hostName,
      mediaTitle: room.mediaTitle,
      isPlaying: room.isPlaying,
      participantCount: room.peers.size,
      isLocked: room.isLocked
    }));
    return;
  }

  // Stream Upload Endpoint (Host uploads local movie to server)
  if (req.method === 'POST' && parsedUrl.pathname.startsWith('/api/v1/stream/upload/')) {
    const rawCode = parsedUrl.pathname.replace('/api/v1/stream/upload/', '').split('/')[0].toUpperCase().trim();
    if (!rawCode) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Room code required' }));
      return;
    }

    const fileName = req.headers['x-file-name'] || 'stream.mp4';
    const mimeType = req.headers['content-type'] || 'video/mp4';
    const filePath = path.join(UPLOADS_DIR, `${rawCode}_stream.mp4`);

    console.log(`[Stream Upload Start] Room ${rawCode} | File: ${fileName} | Type: ${mimeType}`);
    const writeStream = fs.createWriteStream(filePath);

    req.pipe(writeStream);

    writeStream.on('finish', () => {
      try {
        const stats = fs.statSync(filePath);
        roomStreams.set(rawCode, {
          filePath: filePath,
          fileName: decodeURIComponent(fileName),
          mimeType: mimeType,
          size: stats.size,
          createdAt: Date.now()
        });
        console.log(`[Stream Upload Complete] Room ${rawCode} | Size: ${(stats.size / (1024 * 1024)).toFixed(2)} MB`);
        
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          status: 'success',
          streamUrl: `/api/v1/stream/${rawCode}`,
          size: stats.size
        }));
      } catch (err) {
        console.error(`[Stream Stat Error]`, err.message);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Failed to finalize uploaded stream' }));
      }
    });

    writeStream.on('error', (err) => {
      console.error(`[Stream Upload Error] Room ${rawCode}:`, err.message);
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Failed to write stream' }));
    });
    return;
  }

  // Stream Video Endpoint (Guests & Host stream video with HTTP 206 Byte Range Support)
  if ((req.method === 'GET' || req.method === 'HEAD') && parsedUrl.pathname.startsWith('/api/v1/stream/')) {
    const rawCode = parsedUrl.pathname.replace('/api/v1/stream/', '').split('/')[0].toUpperCase().trim();
    const filePath = path.join(UPLOADS_DIR, `${rawCode}_stream.mp4`);

    if (!fs.existsSync(filePath)) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Stream not found or expired' }));
      return;
    }

    try {
      const stat = fs.statSync(filePath);
      const fileSize = stat.size;
      const range = req.headers.range;
      const streamInfo = roomStreams.get(rawCode);
      const contentType = streamInfo?.mimeType || 'video/mp4';

      if (range) {
        // Parse HTTP Range: bytes=start-end
        const parts = range.replace(/bytes=/, "").split("-");
        const start = parseInt(parts[0], 10);
        const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

        if (start >= fileSize) {
          res.writeHead(416, {
            'Content-Range': `bytes */${fileSize}`,
            'Content-Type': 'text/plain'
          });
          res.end();
          return;
        }

        const chunksize = (end - start) + 1;
        res.writeHead(206, {
          'Content-Range': `bytes ${start}-${end}/${fileSize}`,
          'Accept-Ranges': 'bytes',
          'Content-Length': chunksize,
          'Content-Type': contentType
        });

        if (req.method === 'HEAD') {
          res.end();
          return;
        }

        const fileStream = fs.createReadStream(filePath, { start, end });
        fileStream.on('error', (err) => {
          console.error('[Stream Read Error]', err.message);
          if (!res.headersSent) res.writeHead(500);
          res.end();
        });
        req.on('close', () => {
          fileStream.destroy();
        });
        fileStream.pipe(res);
      } else {
        // Entire file response
        res.writeHead(200, {
          'Content-Length': fileSize,
          'Accept-Ranges': 'bytes',
          'Content-Type': contentType
        });

        if (req.method === 'HEAD') {
          res.end();
          return;
        }

        const fileStream = fs.createReadStream(filePath);
        fileStream.on('error', (err) => {
          console.error('[Stream Read Error]', err.message);
          if (!res.headersSent) res.writeHead(500);
          res.end();
        });
        req.on('close', () => {
          fileStream.destroy();
        });
        fileStream.pipe(res);
      }
    } catch (err) {
      console.error('[Stream Serve Error]', err.message);
      if (!res.headersSent) res.writeHead(500);
      res.end();
    }
    return;
  }

  // Default 404
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Endpoint not found' }));
});

// Start Server with port-retry logic
function startServer(portToTry) {
  server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
      console.warn(`⚠️ Port ${portToTry} in use. Trying ${portToTry + 1}...`);
      startServer(portToTry + 1);
    } else {
      console.error('[Server Error]', err);
    }
  });

  server.listen(portToTry, HOST, () => {
    const wss = new WebSocketServer({ server });
    setupWebSocketServer(wss);
    console.log(`\n======================================================`);
    console.log(`🎬 WatchTogether Production Server Started`);
    console.log(`📡 HTTP API:      http://${HOST}:${portToTry}`);
    console.log(`🔌 WebSocket:     ws://${HOST}:${portToTry}`);
    console.log(`❤️ Health Check:  http://${HOST}:${portToTry}/health`);
    console.log(`======================================================\n`);
  });
}

startServer(PORT);
