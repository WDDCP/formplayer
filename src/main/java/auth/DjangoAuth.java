package auth;

import org.springframework.http.HttpHeaders;

/**
 * Created by willpride on 1/13/16.
 */
public class DjangoAuth implements HqAuth {

    String authKey;

    public DjangoAuth(String authKey) {
        this.authKey = authKey;
    }


    @Override
    public HttpHeaders getAuthHeaders() {
        return new HttpHeaders(){
            {
                add( "Cookie",  "sessionid=" + authKey);
                add( "sessionid",  "sessionid=" + authKey);
                add( "Authorization",  "sessionid=" + authKey);
            }
        };
    }
}