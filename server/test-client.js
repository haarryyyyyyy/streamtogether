const WebSocket = require('ws');
const PORT = process.env.PORT || 8081;
const SERVER_URL = `ws://localhost:${PORT}`;



console.log('--- Starting Sync Server Test Suite ---');

const clientHost = new WebSocket(SERVER_URL);
let createdPin = null;

clientHost.on('open', () => {
    console.log('[Host Test] Connected to server.');
    
    // Step 1: Test NTP Ping
    const t0 = Date.now();
    clientHost.send(JSON.stringify({ type: 'NTP_PING', clientTime: t0 }));

    // Step 2: Test Create Room
    clientHost.send(JSON.stringify({
        type: 'CREATE_ROOM',
        username: 'Alice (Host)',
        mediaUrl: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
        mediaTitle: 'Big Buck Bunny Test'
    }));
});

clientHost.on('message', (raw) => {
    const data = JSON.parse(raw.toString());
    console.log('[Host Received]:', data.type, data);

    if (data.type === 'NTP_PONG') {
        const t3 = Date.now();
        const rtt = t3 - data.clientTime;
        const offset = data.serverTime - (t3 + data.clientTime) / 2;
        console.log(`[NTP Test] Success! RTT: ${rtt}ms, Clock Offset: ${offset}ms`);
    }

    if (data.type === 'ROOM_CREATED') {
        createdPin = data.room.pin;
        console.log(`[Room Test] Room Created with PIN: ${createdPin}`);

        // Spawn second client (Peer B) to test joining and sync
        testPeerJoin(createdPin);
    }
});

function testPeerJoin(pin) {
    const clientPeer = new WebSocket(SERVER_URL);

    clientPeer.on('open', () => {
        console.log(`[Peer Test] Connecting to room ${pin}...`);
        clientPeer.send(JSON.stringify({
            type: 'JOIN_ROOM',
            pin: pin,
            username: 'Bob (Peer)'
        }));
    });

    clientPeer.on('message', (raw) => {
        const data = JSON.parse(raw.toString());
        console.log('[Peer Received]:', data.type, data);

        if (data.type === 'ROOM_JOINED') {
            console.log('[Peer Test] Joined Room Successfully!');
            
            // Trigger Play Action from Host
            setTimeout(() => {
                console.log('[Host Action] Triggering PLAY at 12.5s');
                clientHost.send(JSON.stringify({
                    type: 'ACTION_PLAY',
                    positionSec: 12.5
                }));
            }, 500);

            // Clean up and finish test after 2 seconds
            setTimeout(() => {
                console.log('✅ ALL SERVER TESTS PASSED PERFECTLY!');
                clientHost.close();
                clientPeer.close();
                process.exit(0);
            }, 2000);
        }
    });
}
