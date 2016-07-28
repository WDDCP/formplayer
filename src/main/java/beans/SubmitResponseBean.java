package beans;

import beans.menus.ErrorBean;
import session.FormSession;

import java.io.IOException;
import java.util.HashMap;

/**
 * Created by willpride on 1/20/16.
 */
public class SubmitResponseBean extends SessionBean{
    private String status;
    private String message;
    private HashMap<String, ErrorBean> errors;

    // default constructor for Jackson
    public SubmitResponseBean(){}

    public SubmitResponseBean(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public HashMap<String, ErrorBean> getErrors() {
        return errors;
    }

    public void setErrors(HashMap<String, ErrorBean> errors) {
        this.errors = errors;
    }

    @Override
    public String toString(){
        return "SubmitResponseBean, [status=" +  status + ", errors: " + errors +"]";
    }
}
