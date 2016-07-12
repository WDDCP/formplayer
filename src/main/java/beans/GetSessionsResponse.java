package beans;

import objects.SerializableFormSession;

import java.util.Arrays;
import java.util.List;

/**
 * Response containing a list of the user's incomplete form sessions
 */
public class GetSessionsResponse {

    private SessionListItem[] sessions;

    public GetSessionsResponse(){}

    public GetSessionsResponse(List<SerializableFormSession> sessionList){
        sessions = new SessionListItem[sessionList.size()];
        for(int i = 0; i < sessionList.size(); i++){
            sessions[i] = new SessionListItem(sessionList.get(i));
        }
    }

    public SessionListItem[] getSessions() {
        return sessions;
    }

    public void setSessions(SessionListItem[] sessions) {
        this.sessions = sessions;
    }

    @Override
    public String toString(){
        return "GetSessionsResponse sessions=" + Arrays.toString(sessions);
    }
}