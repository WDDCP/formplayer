package tests;

import application.SessionController;
import auth.HqAuth;
import beans.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import objects.SerializableSession;
import org.commcare.api.persistence.SqlSandboxUtils;
import org.commcare.api.persistence.UserSqlSandbox;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import repo.SessionRepo;
import services.RestoreService;
import services.XFormService;
import utils.FileUtils;

import java.io.IOException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by willpride on 2/3/16.
 */
public class BaseTestClass {

    protected MockMvc mockMvc;

    @Autowired
    protected SessionRepo sessionRepoMock;

    @Autowired
    protected XFormService xFormServiceMock;

    @Autowired
    protected RestoreService restoreServiceMock;

    @InjectMocks
    protected SessionController sessionController;

    ObjectMapper mapper;

    final protected SerializableSession serializableSession = new SerializableSession();

    @Before
    public void setUp() throws IOException {
        Mockito.reset(sessionRepoMock);
        Mockito.reset(xFormServiceMock);
        Mockito.reset(restoreServiceMock);
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(sessionController).build();
        when(restoreServiceMock.getRestoreXml(anyString(), any(HqAuth.class)))
                .thenReturn(FileUtils.getFile(this.getClass(), "test_restore_3.xml"));
        mapper = new ObjectMapper();
        SqlSandboxUtils.deleteDatabaseFolder(UserSqlSandbox.DEFAULT_DATBASE_PATH);
        setUpSessionRepoMock();
    }

    public void setUpSessionRepoMock(){

        when(sessionRepoMock.find(anyString())).thenReturn(serializableSession);

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                Object[] args = invocationOnMock.getArguments();
                SerializableSession toBeSaved = (SerializableSession) args[0];
                serializableSession.setInstanceXml(toBeSaved.getInstanceXml());
                serializableSession.setFormXml(toBeSaved.getFormXml());
                serializableSession.setRestoreXml(toBeSaved.getRestoreXml());
                serializableSession.setUsername(toBeSaved.getUsername());
                serializableSession.setSessionData(toBeSaved.getSessionData());
                return null;
            }
        }).when(sessionRepoMock).save(Matchers.any(SerializableSession.class));
    }


    public AnswerQuestionResponseBean answerQuestionGetResult(String index, String answer, String sessionId) throws Exception {
        AnswerQuestionRequestBean answerQuestionBean = new AnswerQuestionRequestBean(index, answer, sessionId);
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(answerQuestionBean);
        MvcResult answerResult = this.mockMvc.perform(
                post("/answer_question")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andReturn();

        AnswerQuestionResponseBean response = mapper.readValue(answerResult.getResponse().getContentAsString(),
                AnswerQuestionResponseBean.class);
        return response;
    }

    public JSONObject startNewSession(String requestPath, String formPath) throws Exception {

        when(xFormServiceMock.getFormXml(anyString(), any(HqAuth.class)))
                .thenReturn(FileUtils.getFile(this.getClass(), formPath));
        String requestPayload = FileUtils.getFile(this.getClass(), requestPath);

        NewSessionRequestBean newSessionRequestBean = mapper.readValue(requestPayload,
                NewSessionRequestBean.class);
        MvcResult result = this.mockMvc.perform(
                post("/new_session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(newSessionRequestBean))).andReturn();
        String responseBody = result.getResponse().getContentAsString();
        JSONObject ret = new JSONObject(responseBody);
        return ret;
    }

    public CaseFilterResponseBean filterCases(String requestPath) throws Exception {

        String filterRequestPayload = FileUtils.getFile(this.getClass(), requestPath);
        MvcResult result = this.mockMvc.perform(
                post("/filter_cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterRequestPayload))
                .andExpect(status().isOk())
                .andReturn();

         return mapper.readValue(result.getResponse().getContentAsString(),
                CaseFilterResponseBean.class);
    }

    public SubmitResponseBean submitForm(String requestPath, String sessionId) throws Exception {
        SubmitRequestBean submitRequestBean = mapper.readValue
                (FileUtils.getFile(this.getClass(), requestPath), SubmitRequestBean.class);
        submitRequestBean.setSessionId(sessionId);

        String result = this.mockMvc.perform(
                post("/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(submitRequestBean)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readValue(result, SubmitResponseBean.class);
    }

    public SyncDbResponseBean syncDb(String requestPath) throws Exception {
        String syncDbRequestPayload = FileUtils.getFile(this.getClass(), "requests/sync_db/sync_db.json");

        SyncDbRequestBean syncDbRequestBean = mapper.readValue(syncDbRequestPayload,
                SyncDbRequestBean.class);

        MvcResult result = this.mockMvc.perform(
                post("/sync_db")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(syncDbRequestBean)))
                .andExpect(status().isOk())
                .andReturn();

        SyncDbResponseBean syncDbResponseBean = mapper.readValue(result.getResponse().getContentAsString(),
                SyncDbResponseBean.class);
        return syncDbResponseBean;
    }

    public RepeatResponseBean newRepeatRequest(String path, String sessionId) throws Exception {

        String newRepeatRequestPayload = FileUtils.getFile(this.getClass(), path);

        RepeatRequestBean newRepeatRequestBean = mapper.readValue(newRepeatRequestPayload,
                RepeatRequestBean.class);
        newRepeatRequestBean.setSessionId(sessionId);

        String newRepeatRequestString = mapper.writeValueAsString(newRepeatRequestBean);

        String repeatResult = mockMvc.perform(get("/new_repeat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(newRepeatRequestString)).andReturn().getResponse().getContentAsString();
        return mapper.readValue(repeatResult, RepeatResponseBean.class);
    }

    public RepeatResponseBean deleteRepeatRequest(String path, String sessionId) throws Exception {

        String newRepeatRequestPayload = FileUtils.getFile(this.getClass(), path);

        RepeatRequestBean newRepeatRequestBean = mapper.readValue(newRepeatRequestPayload,
                RepeatRequestBean.class);
        newRepeatRequestBean.setSessionId(sessionId);

        String newRepeatRequestString = mapper.writeValueAsString(newRepeatRequestBean);

        String repeatResult = mockMvc.perform(get("/delete_repeat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(newRepeatRequestString)).andReturn().getResponse().getContentAsString();
        return mapper.readValue(repeatResult, RepeatResponseBean.class);
    }

    @After
    public void tearDown(){
        SqlSandboxUtils.deleteDatabaseFolder(UserSqlSandbox.DEFAULT_DATBASE_PATH);
    }
}