package com.atonamy.justcallme;

import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

/**
 * Created by archie (Arseniy Kucherenko) on 9/1/16.
 */
public class SignalingHandler  {


    public interface Events {
        public void onInitiator(final List<PeerConnection.IceServer> iceServers);
        public void onServerConnected(final String connectionId);
        public void onInterlocutorDisconnected();
        public void onInterlocutorFound(final String peerId);
        public void onRemoteDescription(final SessionDescription description, final List<PeerConnection.IceServer> iceServers);
        public void onRemoteCandidate(final IceCandidate candidate);
        public void onError(final Integer code, final String message);
    }

    private String wsServer;
    private Events iEvents;
    private List<PeerConnection.IceServer> iceServers = null;
    private List<IceCandidate> iceCandidates = null;
    private Map<Integer, IceCandidate> queueCandidates = null;
    private boolean connectionStatus;
    private Boolean isInitiator;
    private WebSocketConnection wsConnection;
    private String clientId;
    private String interlocutorId;
    private String currentCommandId;
    private boolean isQueue;
    private boolean peerDisconnected;
    private int candidatePriority;
    private boolean descriptionSent;
    private int nextCandidatePriority;


    private static final int DEFAULT_PARAMS = 3;

    protected enum Status {
        NO_STATUS(0), ACCEPTED(1), RECEIVED(2), CONNECTED(3), PEER_FOUND(4), IN_QUEUE(5),
        DISCONNECTED(6), FAIL(7), NO_PEERS(8), NO_ROOM(9), NO_CONNECTION(10), TURN(15);

        private final int value;

        private Status(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean Compare(int i){
            return value == i;
        }

        public static Status fromInteger(int value) {
            Status[] values = Status.values();
            for(int i = 0; i < values.length; i++)
            {
                if(values[i].Compare(value))
                    return values[i];
            }
            return Status.NO_STATUS;
        }
    }

    public SignalingHandler(Events events, String wsUri) {
        wsServer = wsUri;
        iEvents = events;
        initDefaults();
    }

    private void initDefaults() {

        connectionStatus = false;
        wsConnection = new WebSocketConnection();
        if(iceServers == null)
            iceServers = new LinkedList<PeerConnection.IceServer>();
        else
            iceServers.clear();
        if(queueCandidates == null)
            queueCandidates = new HashMap<Integer, IceCandidate>();
        else
            queueCandidates.clear();
        if(iceCandidates == null)
            iceCandidates = new LinkedList<IceCandidate>();
        else
            iceCandidates.clear();

        isQueue = false;
        clientId = null;
        interlocutorId = null;
        isInitiator = null;
        currentCommandId = UUID.randomUUID().toString();
        peerDisconnected = false;
        candidatePriority = 0;
        nextCandidatePriority = 0;
        descriptionSent = false;
    }


    public void connect() {
        disconnect();
        initDefaults();
        boolean fail = false;
        if (!connectionStatus) {
            try {
                wsConnection.connect(new URI(wsServer), wsObserver);
            } catch (WebSocketException e) {
                e.printStackTrace();
                fail = true;
            } catch (URISyntaxException e) {
                e.printStackTrace();
                fail = true;
            }
        }

        if(iEvents != null && fail)
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {
                    //need to replace on proper error code and message
                    iEvents.onError(null, null);
                }
            });
    }

    public void disconnect() {
        if(connectionStatus)
            wsConnection.disconnect();
    }

    public boolean getConnectionStatus() {
        return connectionStatus;
    }

    public void sendLocalDescription(boolean initiator, SessionDescription description) {

        sendMessage(getDescriptionMessage(description.description));
    }

    public void sendLocalCandidate(IceCandidate candidate) {

        sendMessage(getCandidateMessage(candidate));
    }

    public void descriptionSent() {
        descriptionSent = true;
    }

    protected boolean handleJsonResponse(String json) {
        Log.w("WSS", json);
        JSONObject response_json = null;
        Status status = Status.NO_STATUS;
        String id = null;
        try {
            response_json = new JSONObject(json);
            status = Status.fromInteger(response_json.getInt("StatusCode"));
            id = response_json.getString("Id");
            response_json.getString("Status");

            switch(status) {
                case ACCEPTED:
                    return handleAcceptedResponse(response_json, id);
                case CONNECTED:
                    return handleConnectedResponse(response_json, id);
                case DISCONNECTED:
                    return handleDisconnectedResponse(response_json, id);
                case RECEIVED:
                    return handleReceivedResponse(response_json, id);
                case PEER_FOUND:
                    return handlePeerFoundResponse(response_json, id);
                case IN_QUEUE:
                    return handleInQueueResponse(response_json, id);
                case TURN:
                    return handleInTurnResponse(response_json, id);
                case NO_CONNECTION:
                    return handleNoConnectionResponse(response_json, id);
                case NO_ROOM:
                    return handleNoRoomNoPeersResponse(response_json, id);
                case NO_PEERS:
                    return handleNoRoomNoPeersResponse(response_json, id);
                case FAIL:
                    return handleFailResponse(response_json, id);
                default:
                    return false;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }

    protected boolean handleAcceptedResponse(JSONObject responseJson, String id) throws JSONException {
        if(responseJson.length() != DEFAULT_PARAMS+1 ||
                !responseJson.getString("To").contentEquals(interlocutorId) ||
                !id.contentEquals(currentCommandId + "-" + Status.RECEIVED.getValue()))
            return false;

        return true;
    }

    protected boolean handleConnectedResponse(JSONObject responseJson, String id) throws JSONException {
        if(responseJson.length() != DEFAULT_PARAMS+1 || clientId != null)
            return false;
        clientId = responseJson.getString("ConnectionId");
        if(iEvents != null)
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {
                    iEvents.onServerConnected(clientId);
                }
            });
        return true;
    }

    protected boolean handleDisconnectedResponse(JSONObject responseJson, String id) throws JSONException {

        if(responseJson.length() != DEFAULT_PARAMS+1 &&
                !responseJson.getString("ConnectionId").contentEquals(interlocutorId))
            return false;

        if(iEvents != null && !peerDisconnected) {
            peerDisconnected = true;
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {
                    iEvents.onInterlocutorDisconnected();
                }
            });
        }


        return true;
    }

    protected boolean handleNoRoomNoPeersResponse(JSONObject responseJson, String id) throws JSONException {
        if(responseJson.length() != DEFAULT_PARAMS || interlocutorId != null)
            return false;
        sendCommand(currentCommandId + "-" + Status.IN_QUEUE.getValue(), "QUEUE", null);
        return true;
    }

    protected boolean handleReceivedResponse(JSONObject responseJson, String id) throws JSONException {
        String from = responseJson.getString("From");
        if(responseJson.length() != DEFAULT_PARAMS+2 &&
                !from.contentEquals(interlocutorId))
            return false;

        final JSONObject message = new JSONObject(responseJson.getString("Message"));
        final String messageType = message.getString("type");

        if(iEvents != null)
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {

                    try {
                        if(messageType.contentEquals("offer") || messageType.contentEquals("answer")) {

                            final SessionDescription sessionDescription = new SessionDescription(
                                    SessionDescription.Type.fromCanonicalForm(messageType),
                                    message.getString("sdp"));
                            iEvents.onRemoteDescription(sessionDescription, iceServers);
                            if (iceCandidates.size() > 0) {
                                Iterator<IceCandidate> i_candidates = iceCandidates.iterator();
                                while (i_candidates.hasNext())
                                    iEvents.onRemoteCandidate(i_candidates.next());
                                iceCandidates.clear();
                            }
                        }
                        else if(messageType.contentEquals("candidate")) {
                            IceCandidate candidate = new IceCandidate(
                                    message.getString("id"),
                                    message.getInt("label"),
                                    message.getString("candidate"));
                            Integer priority = message.getInt("priority");
                            List<IceCandidate> candidates = getCandidatesToSend(priority, candidate);
                            Iterator<IceCandidate> i_candidates = candidates.iterator();

                            if (descriptionSent)
                                while (i_candidates.hasNext())
                                    iEvents.onRemoteCandidate(i_candidates.next());
                            else
                                while (i_candidates.hasNext())
                                    iceCandidates.add(priority, candidate);
                            }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        //need to replace on proper error code and message
                        iEvents.onError(null, null);
                    }
                }
            });

        return true;
    }

    protected List<IceCandidate> getCandidatesToSend(int priority, IceCandidate candidate) {

        ArrayList<IceCandidate> result = new ArrayList<IceCandidate>();
        boolean added = false;
        if(nextCandidatePriority == priority){
            result.add(candidate);
            nextCandidatePriority++;
            added = true;
        }

        boolean found = true;
        while (found) {
            found = false;
            List<Integer> remove = new ArrayList<Integer>();
            for (int c : queueCandidates.keySet())
                if (c == nextCandidatePriority) {
                    result.add (queueCandidates.get(c));
                    remove.add (c);
                    found = true;
                    nextCandidatePriority++;
                }
            for (int r : remove)
                queueCandidates.remove (r);

        }

        if (nextCandidatePriority == priority) {
            result.add (candidate);
            nextCandidatePriority++;
        }
        else if(!added)
            queueCandidates.put (priority, candidate);

        return result;
    }

    protected boolean handlePeerFoundResponse(JSONObject responseJson, String id) throws JSONException {
        if(responseJson.length() != DEFAULT_PARAMS+2 || interlocutorId != null || !id.contentEquals(""))
            return false;

        interlocutorId = responseJson.getString("Peer");
        isInitiator = responseJson.getBoolean("IsInitiator");

        if(iEvents != null)
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {
                    iEvents.onInterlocutorFound(interlocutorId);
                }
            });
        if(isInitiator != null && !isInitiator)
            sendCommand(currentCommandId + "-" + Status.TURN.getValue(), "turn", null);

        return true;
    }

    protected boolean handleInQueueResponse(JSONObject responseJson, String id) throws JSONException {
        if(responseJson.length() != DEFAULT_PARAMS || isQueue ||
                (!id.contentEquals("") && !id.contentEquals(currentCommandId + "-" + Status.IN_QUEUE.getValue())))
            return false;

        isInitiator = true;
        sendCommand(currentCommandId + "-" + Status.TURN.getValue(), "turn", null);
        isQueue = true;

        return true;
    }

    protected boolean handleNoConnectionResponse(JSONObject responseJson, String id) {
        if(responseJson.length() != DEFAULT_PARAMS)
            return false;

        disconnect();

        return true;
    }

    protected boolean handleFailResponse(JSONObject responseJson, String id) throws JSONException {

        if(responseJson.length() != DEFAULT_PARAMS+1 || !responseJson.getString("To").contentEquals(interlocutorId))
            return false;

        if(iEvents != null && !peerDisconnected) {
            peerDisconnected = true;
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {
                    iEvents.onInterlocutorDisconnected();
                }
            });
        }

        return true;
    }

    protected boolean handleInTurnResponse(JSONObject responseJson, String id) throws JSONException {
        if(responseJson.length() != DEFAULT_PARAMS+4  || !id.contentEquals(currentCommandId + "-" + Status.TURN.getValue()))
            return false;

        String usernamer = responseJson.getString("Username");
        String password = responseJson.getString("Password");
        JSONArray uris = responseJson.getJSONArray("Uris");
        for (int i = 0; i < uris.length(); i++) {
            PeerConnection.IceServer ice = new PeerConnection.IceServer(uris.getString(i), usernamer, password);
            iceServers.add(ice);
        }


        if(isInitiator != null && isInitiator && iEvents != null)
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {
                    iEvents.onInitiator(iceServers);
                }
            });

        return true;
    }

    protected void sendCommand(String codeId, String command, HashMap<String, String> params) throws JSONException {
        JSONObject request_json = new JSONObject();
        request_json.put("Id", codeId);
        request_json.put("Command", command);
        if(params != null) {
            for(String param : params.keySet())
                request_json.put(param, params.get(param));
        }
        if(wsConnection != null && connectionStatus)
            wsConnection.sendTextMessage(request_json.toString());
        else if(iEvents != null)
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {
                    //need to replace on proper error code and message
                    iEvents.onError(null, null);
                }
            });

    }

    protected void sendMessage(String message) {


        if(message != null && connectionStatus && interlocutorId != null) {

            HashMap<String, String> params = new HashMap<String, String>();

            params.put("To", interlocutorId);
            params.put("Message", message.toString());

            try {
                sendCommand(currentCommandId + "-" + Status.RECEIVED.getValue(), "send", params);
            } catch (JSONException e) {
                e.printStackTrace();
                if(iEvents != null)
                    (new Handler()).post(new Runnable() {
                        @Override
                        public void run() {
                            //need to replace on proper error code and message
                            iEvents.onError(null, null);
                        }
                    });
            }
        }
        else if(iEvents != null)
            (new Handler()).post(new Runnable() {
                @Override
                public void run() {
                    //need to replace on proper error code and message
                    iEvents.onError(null, null);
                }
            });


    }
    protected String getDescriptionMessage(String description) {
        JSONObject message = new JSONObject();

        if(isInitiator == null)
            return null;

        try {
            message.put("sdp", description);
            message.put("type", (isInitiator) ? "offer" : "answer");
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return message.toString();
    }

    protected String getCandidateMessage(IceCandidate candidate) {
        JSONObject message = new JSONObject();

        try {
            message.put("type", "candidate");
            message.put("label", candidate.sdpMLineIndex);
            message.put("id", candidate.sdpMid);
            message.put("candidate", candidate.sdp);
            message.put("priority", candidatePriority++);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return message.toString();
    }

    private WebSocket.WebSocketConnectionObserver wsObserver = new WebSocket.WebSocketConnectionObserver() {

        @Override
        public void onOpen() {
            connectionStatus = true;
        }

        @Override
        public void onClose(WebSocketCloseNotification webSocketCloseNotification, String s) {
            connectionStatus = false;
            if(iEvents != null && !peerDisconnected) {
                peerDisconnected = true;
                (new Handler()).post(new Runnable() {
                    @Override
                    public void run() {
                        iEvents.onInterlocutorDisconnected();
                    }
                });
            }

        }

        @Override
        public void onTextMessage(String message) {

            Log.w("WSS", message);
            if(!handleJsonResponse(message) && iEvents != null)
                (new Handler()).post(new Runnable() {
                    @Override
                    public void run() {
                        //need to replace on proper error code and message
                        iEvents.onError(null, null);
                    }
                });
        }

        @Override
        public void onRawTextMessage(byte[] bytes) {

        }

        @Override
        public void onBinaryMessage(byte[] bytes) {

        }
    };


}