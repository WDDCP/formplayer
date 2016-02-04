package tests;

import application.SessionController;
import auth.HqAuth;
import beans.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import objects.SerializableSession;
import org.commcare.api.persistence.SqlSandboxUtils;
import org.commcare.api.persistence.UserSqlSandbox;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import repo.SessionRepo;
import services.RestoreService;
import services.XFormService;
import utils.FileUtils;
import utils.TestContext;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestContext.class)
public class FormEntryTest extends BaseTestClass{

    //Integration test of form entry functions
    @Test
    public void testFormEntry() throws Exception {

        serializableSession.setRestoreXml(FileUtils.getFile(this.getClass(), "test_restore.xml"));

        when(xFormServiceMock.getFormXml(anyString(), any(HqAuth.class)))
                .thenReturn(FileUtils.getFile(this.getClass(), "xforms/question_types.xml"));

        String requestPayload = FileUtils.getFile(this.getClass(), "requests/new_form/new_form_2.json");

        ObjectMapper mapper = new ObjectMapper();
        NewSessionRequestBean newFormRequest = mapper.readValue(requestPayload, NewSessionRequestBean.class);

        ResultActions result = mockMvc.perform(post("/new_session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(newFormRequest)));
        JSONObject resultJson = new JSONObject(result.andReturn().getResponse().getContentAsString());

        String sessionId = resultJson.getString("session_id");

        AnswerQuestionResponseBean response = answerQuestionGetResult("1","William Pride", sessionId);

        response = answerQuestionGetResult("2","345", sessionId);
        response = answerQuestionGetResult("3","2.54", sessionId);
        response = answerQuestionGetResult("4","1970-10-23", sessionId);
        response = answerQuestionGetResult("6", "12:30:30", sessionId);
        response = answerQuestionGetResult("7", "ben rudolph", sessionId);
        response = answerQuestionGetResult("8","123456789", sessionId);
        response = answerQuestionGetResult("10", "2",sessionId);
        response = answerQuestionGetResult("11", "1 2 3", sessionId);

        //Test Current Session
        CurrentRequestBean currentRequestBean = mapper.readValue
                (FileUtils.getFile(this.getClass(), "requests/current/current_request.json"), CurrentRequestBean.class);
        currentRequestBean.setSessionId(sessionId);

        ResultActions currentResult = mockMvc.perform(get("/current")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(currentRequestBean)));
        String currentResultString = currentResult.andReturn().getResponse().getContentAsString();

        //Test Get Instance
        GetInstanceRequestBean getInstanceRequestBean = mapper.readValue
                (FileUtils.getFile(this.getClass(), "requests/current/current_request.json"), GetInstanceRequestBean.class);
        getInstanceRequestBean.setSessionId(sessionId);
        ResultActions getInstanceResult = mockMvc.perform(get("/get_instance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(getInstanceRequestBean)));
        String getInstanceResultString = getInstanceResult.andReturn().getResponse().getContentAsString();

        //Test Get Instance
        EvaluateXPathRequestBean evaluateXPathRequestBean = mapper.readValue
                (FileUtils.getFile(this.getClass(), "requests/evaluate_xpath/evaluate_xpath.json"), EvaluateXPathRequestBean.class);
        evaluateXPathRequestBean.setSessionId(sessionId);
        ResultActions evaluateXpathResult = mockMvc.perform(get("/evaluate_xpath")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(evaluateXPathRequestBean)));
        String evaluateXpathResultString = evaluateXpathResult.andReturn().getResponse().getContentAsString();
        EvaluateXPathResponseBean evaluateXPathResponseBean = mapper.readValue(evaluateXpathResultString,
                EvaluateXPathResponseBean.class);
        assert evaluateXPathResponseBean.getStatus().equals("success");
        assert evaluateXPathResponseBean.getOutput().equals("William Pride");

        //Test Submission
        SubmitRequestBean submitRequestBean = mapper.readValue
                (FileUtils.getFile(this.getClass(), "requests/submit/submit_request.json"), SubmitRequestBean.class);
        submitRequestBean.setSessionId(sessionId);

        ResultActions submitResult = mockMvc.perform(post("/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(submitRequestBean)));
        String submitResultString = submitResult.andReturn().getResponse().getContentAsString();
    }
}