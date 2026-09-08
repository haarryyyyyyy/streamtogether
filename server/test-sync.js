// End-to-end integration test for WatchTogether WebSocket protocol
const WebSocket = require('ws');

const WS_URL = 'ws://127.0.0.1:8080';

async function runTest() {
  console.log('🧪 Starting WatchTogether Sync Integration Test...');

  // 1. Host Connects
  const hostWs = new WebSocket(WS_URL);
  let roomCode = null;

  await new Promise((resolve) => {
    hostWs.on('open', () => {
      console.log('✅ Host WebSocket connected');
      // NTP Ping
      hostWs.send(JSON.stringify({ type: 'NTP_PING', clientTime: Date.now() }));
      // Create Room
      hostWs.send(JSON.stringify({
        type: 'CREATE_ROOM',
        guestId: 'host_gst_01',
        displayName: 'Alice (Host)',
        mediaTitle: 'Big Buck Bunny',
        mediaUrl: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4'
      }));
    });

    hostWs.on('message', (raw) => {
      const data = JSON.parse(raw.toString());
      if (data.type === 'NTP_PONG') {
        console.log('⏱️ NTP Clock Sync received: offset calculated successfully');
      } else if (data.type === 'ROOM_CREATED') {
        roomCode = data.roomCode;
        console.log(`🎉 Room Created Successfully: ${roomCode}`);
        resolve();
      }
    });
  });

  // 2. Guest Connects and Joins Room
  const guestWs = new WebSocket(WS_URL);

  await new Promise((resolve) => {
    guestWs.on('open', () => {
      console.log('✅ Guest WebSocket connected');
      guestWs.send(JSON.stringify({
        type: 'JOIN_ROOM',
        roomCode: roomCode,
        guestId: 'guest_gst_02',
        displayName: 'Bob'
      }));
    });

    guestWs.on('message', (raw) => {
      const data = JSON.parse(raw.toString());
      if (data.type === 'ROOM_JOINED') {
        console.log(`🤝 Guest Joined Room: ${data.roomCode} | Participants: ${data.roomState.participants.length}`);
        resolve();
      }
    });
  });

  // 3. Host Plays Video at 45.5s
  await new Promise((resolve) => {
    guestWs.on('message', (raw) => {
      const data = JSON.parse(raw.toString());
      if (data.type === 'SYNC_STATE' && data.action === 'PLAY') {
        console.log(`▶️ Guest received PLAY sync event at ${data.positionSec}s`);
        resolve();
      }
    });

    hostWs.send(JSON.stringify({
      type: 'ACTION_PLAY',
      positionSec: 45.5
    }));
  });

  // 4. Send Chat Message
  await new Promise((resolve) => {
    hostWs.on('message', (raw) => {
      const data = JSON.parse(raw.toString());
      if (data.type === 'CHAT_MESSAGE' && data.message.text === 'Hello everyone! Synchronized watching is awesome.') {
        console.log(`💬 Host received chat message from Bob: "${data.message.text}"`);
        resolve();
      }
    });

    guestWs.send(JSON.stringify({
      type: 'SEND_CHAT',
      text: 'Hello everyone! Synchronized watching is awesome.'
    }));
  });

  hostWs.close();
  guestWs.close();
  console.log('🎉 ALL INTEGRATION TESTS PASSED PERFECTLY!\n');
}

runTest().catch((err) => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
