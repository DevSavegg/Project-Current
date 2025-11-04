import ws from 'k6/ws';
import { check, sleep, group } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const WEBSOCKET_URL = 'ws://localhost:8080/chat';

// --- Custom Metrics ---
let receivedSystemMessages = new Counter('ws_msgs_system');
let receivedChatMessages = new Counter('ws_msgs_chat');
let receivedDMMessages = new Counter('ws_msgs_dm');
let receivedErrorMessages = new Counter('ws_msgs_error');

let createRoomTrend = new Trend('create_room_duration_ms', true);
let joinRoomTrend = new Trend('join_room_duration_ms', true);

// --- Test Options ---
export const options = {
    scenarios: {
        // Scenario A: "Lurkers"
        lurker_scenario: {
            executor: 'ramping-vus',
            exec: 'lurker',
            stages: [
                { duration: '30s', target: 400 },
                { duration: '1m', target: 400 },
                { duration: '10s', target: 0 },
            ],
        },

        // Scenario B: "Chatters"
        chatter_scenario: {
            executor: 'ramping-vus',
            exec: 'chatter',
            stages: [
                { duration: '30s', target: 400 },
                { duration: '1m', target: 400 },
                { duration: '10s', target: 0 },
            ],
            
            startTime: '30s',
        },

        // Scenario C: "Room Hoppers"
        room_hopper_scenario: {
            executor: 'per-vu-iterations',
            exec: 'roomHopper',
            vus: 200,
            iterations: 5,
            maxDuration: '5m',
            startTime: '30s',
        }
    },

    thresholds: {
        'checks': ['rate>0.95'], // more than 95% of checks must pass
        'ws_msgs_error': ['count==0'], // want zero error payloads
    },
};

// --- k6 Setup Function ---
export function setup() {
    console.log('--- k6 setup starting ---');

    const setupPromise = new Promise((resolve, reject) => {
        const url = `${WEBSOCKET_URL}?username=k6_setup_vu`;

        ws.connect(url, null, (socket) => {
            let mainRoomInviteCode = null;

            socket.on('open', () => {
                console.log('[setup] WebSocket connected. Creating main room...');
                socket.send('create_room k6-main-room');
            });

            socket.on('message', (data) => {
                const payload = JSON.parse(data);

                if (payload.type === 'SYSTEM' && payload.subType === 'ROOM_CREATED') {
                    const msg = payload.message;
                    mainRoomInviteCode = msg.split(': ').pop();

                    if (mainRoomInviteCode) {
                        console.log(`[setup] Main room created. Invite code: ${mainRoomInviteCode}`);
                        socket.close();
                    } else {
                        reject('Could not parse invite code from: ' + msg);
                    }
                }
            });

            socket.on('close', () => {
                console.log('[setup] WebSocket closed. Returning data to VUs.');
                resolve({ mainRoomInviteCode });
            });

            socket.on('error', (e) => {
                console.error('[setup] WebSocket error: ', e.error());
                reject('Setup failed: ' + e.error());
            });

            socket.setTimeout(() => {
                reject('Setup timed out');
            }, 10000);
        });
    });

    return setupPromise.then(data => data);
}

// --- Helper: Process Common Payload ---
function processCommonPayload(payload, vu) {
    if (payload.type === 'SYSTEM') receivedSystemMessages.add(1);
    else if (payload.type === 'CHAT') receivedChatMessages.add(1);
    else if (payload.type === 'DM') receivedDMMessages.add(1);
    else if (payload.type === 'ERROR') receivedErrorMessages.add(1);

    check(payload, { 'payload is not ERROR': (p) => p.type !== 'ERROR' });

    if (payload.type === 'ERROR') {
        console.error(`VU ${vu}: Received ERROR: ${payload.message} (Command: ${payload.command})`);
        return false;
    }
    return true;
}

// --- Scenario A: Lurker ---
export function lurker(data) {
    const username = `lurker_${__VU}`;
    const url = `${WEBSOCKET_URL}?username=${username}`;

    const res = ws.connect(url, null, (socket) => {
        let myClientId = null;
        let joinTime = null;

        socket.on('open', () => {
            // console.log(`VU ${__VU} (Lurker): Connected.`);
        });

        socket.on('message', (message) => {
            const payload = JSON.parse(message);
            
            if (!processCommonPayload(payload, __VU)) {
                return; // Stop if it was an ERROR
            }

            if (payload.type === 'SYSTEM' && payload.subType === 'WELCOME') {
                myClientId = payload.message.split(': ').pop();
                check(myClientId, { 'extracted client ID': (id) => id && id.startsWith('user-') });

                // 1. Join the main room
                joinTime = Date.now();
                socket.send(`join_room ${data.mainRoomInviteCode}`);
            } 
            else if (payload.type === 'SYSTEM' && payload.subType === 'USER_JOIN') {
                // 2. Check if this USER_JOIN message is for us
                if (payload.details && payload.details.userId === myClientId) {
                    joinRoomTrend.add(Date.now() - joinTime);
                }
            }
        });

        socket.on('close', () => {
            // console.log(`VU ${__VU} (Lurker): Disconnected.`);
        });

        socket.on('error', (e) => {
            console.error(`VU ${__VU} (Lurker): WebSocket error: ${e.error()}`);
        });
        
        socket.setTimeout(() => {
            socket.close();
        }, 150000); 
    });

    check(res, { 'Lurker WS connection successful': (r) => r && r.status === 101 });
};

// --- Scenario B: Chatter ---
export function chatter(data) {
    const username = `chatter_${__VU}`;
    const url = `${WEBSOCKET_URL}?username=${username}`;

    const res = ws.connect(url, null, (socket) => {
        let myClientId = null;
        let chatInterval = null;

        socket.on('open', () => {
            // console.log(`VU ${__VU} (Chatter): Connected.`);
        });

        socket.on('message', (message) => {
            const payload = JSON.parse(message);

            if (!processCommonPayload(payload, __VU)) {
                return;
            }

            if (payload.type === 'SYSTEM' && payload.subType === 'WELCOME') {
                myClientId = payload.message.split(': ').pop();
                check(myClientId, { 'extracted client ID': (id) => id && id.startsWith('user-') });

                // 1. Join the main room
                socket.send(`join_room ${data.mainRoomInviteCode}`);
                
                // 2. Set up the chat interval after we've joined
                chatInterval = socket.setInterval(() => {
                    const msg = `say Hello from ${username}! The time is ${Date.now()}`;
                    socket.send(msg);
                }, 3000 + (Math.random() * 4000));
            }
        });

        socket.on('close', () => {
            // console.log(`VU ${__VU} (Chatter): Disconnected.`);
        });

        socket.on('error', (e) => {
            console.error(`VU ${__VU} (Chatter): WebSocket error: ${e.error()}`);
        });

        socket.setTimeout(() => {
            if (chatInterval) {
                socket.clearInterval(chatInterval);
            }
            socket.send('leave_room');
            socket.close();
        }, 150000);
    });
    
    check(res, { 'Chatter WS connection successful': (r) => r && r.status === 101 });
};

// --- Scenario C: Room Hopper ---
export function roomHopper(data) {
    const username = `hopper_${__VU}_${__ITER}`;
    const url = `${WEBSOCKET_URL}?username=${username}`;

    group('Room Hopper Lifecycle', () => {
        const res = ws.connect(url, null, (socket) => {
            let myClientId = null;
            let createTime = null;

            let state = 'WAITING_FOR_WELCOME';

            socket.on('open', () => {
                // console.log(`VU ${__VU} (Hopper): Connected.`);
            });

            socket.on('message', (message) => {
                const payload = JSON.parse(message);

                if (!processCommonPayload(payload, __VU)) {
                    return;
                }

                // --- State Machine ---

                if (state === 'WAITING_FOR_WELCOME' && payload.type === 'SYSTEM' && payload.subType === 'WELCOME') {
                    myClientId = payload.message.split(': ').pop();
                    check(myClientId, { 'extracted client ID': (id) => id && id.startsWith('user-') });

                    createTime = Date.now();
                    const roomName = `k6_hopper_room_${__VU}_${__ITER}`;
                    socket.send(`create_room ${roomName}`);
                    
                    state = 'WAITING_FOR_ROOM_CREATION';

                } else if (state === 'WAITING_FOR_ROOM_CREATION' && payload.type === 'SYSTEM' && payload.subType === 'ROOM_CREATED') {
                    state = 'IN_ROOM'; 
                    createRoomTrend.add(Date.now() - createTime);

                    const myRoomInviteCode = payload.message.split(': ').pop();
                    check(myRoomInviteCode, { 'hopper extracted invite code': (c) => c && c.length > 0 });

                    socket.setTimeout(() => {
                        socket.send(`say Hello from my new room!`);
                    }, 1000); // 1s

                    socket.setTimeout(() => {
                        socket.send(`say It's nice here.`);
                    }, 3000); // 1s + 2s

                    socket.setTimeout(() => {
                        socket.send(`list users`);
                    }, 5000); // 3s + 2s

                    socket.setTimeout(() => {
                        socket.send(`leave_room`);
                    }, 6000); // 5s + 1s

                    socket.setTimeout(() => {
                        socket.send(`join_room ${data.mainRoomInviteCode}`);
                    }, 9000); // 6s + 3s

                    socket.setTimeout(() => {
                        socket.close();
                    }, 14000); // 9s + 5s
                }
            });

            socket.on('close', () => {
                // console.log(`VU ${__VU} (Hopper): Disconnected.`);
            });
            
            socket.on('error', (e) => {
                console.error(`VU ${__VU} (Hopper): WebSocket error: ${e.error()}`);
            });

            socket.setTimeout(() => {
                socket.close();
            }, 60000);
        });

        check(res, { 'Hopper WS connection successful': (r) => r && r.status === 101 });
    });
};