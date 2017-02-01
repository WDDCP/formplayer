package qa.steps;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.json.JSONObject;
import qa.TestFailException;
import qa.TestState;
import util.Constants;

/**
 * Created by willpride on 2/1/17.
 */
public class InstallStep implements StepDefinition {
    @Override
    public String getRegularExpression() {
        return "I install the app";
    }

    @Override
    public JSONObject getPostBody(JSONObject lastResponse, TestState currentState, String[] args) throws JsonProcessingException {
        return new JSONObject();
    }


    @Override
    public String getUrl() {
        return Constants.URL_MENU_NAVIGATION;
    }

    @Override
    public void doWork(JSONObject lastResponse, TestState currentState, String[] args) throws TestFailException {

    }
}
