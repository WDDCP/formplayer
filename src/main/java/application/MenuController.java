package application;

import annotations.AppInstall;
import annotations.UserLock;
import annotations.UserRestore;
import auth.HqAuth;
import beans.InstallRequestBean;
import beans.NewFormResponse;
import beans.NotificationMessage;
import beans.SessionNavigationBean;
import beans.menus.BaseResponseBean;
import beans.menus.EntityDetailListResponse;
import beans.menus.EntityDetailResponse;
import beans.menus.UpdateRequestBean;
import exceptions.FormNotFoundException;
import exceptions.MenuNotFoundException;
import hq.CaseAPIs;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.commcare.util.screen.CommCareSessionException;
import org.commcare.util.screen.EntityScreen;
import org.commcare.util.screen.Screen;
import org.javarosa.core.model.instance.TreeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import repo.SerializableMenuSession;
import screens.FormplayerQueryScreen;
import screens.FormplayerSyncScreen;
import services.CategoryTimingHelper;
import services.QueryRequester;
import services.SyncRequester;
import session.FormSession;
import session.MenuSession;
import sqlitedb.ApplicationDB;
import util.Constants;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * Controller (API endpoint) containing all session navigation functionality.
 * This includes module, form, case, and session (incomplete form) selection.
 */
@Api(value = "Menu Controllers", description = "Operations for navigating CommCare Menus and Cases")
@RestController
@EnableAutoConfiguration
public class MenuController extends AbstractBaseController {

    @Autowired
    private QueryRequester queryRequester;

    @Autowired
    private SyncRequester syncRequester;

    @Autowired
    private CategoryTimingHelper categoryTimingHelper;

    private final Log log = LogFactory.getLog(MenuController.class);

    @ApiOperation(value = "Install the application at the given reference")
    @RequestMapping(value = Constants.URL_INSTALL, method = RequestMethod.POST)
    @UserLock
    @UserRestore
    @AppInstall
    public BaseResponseBean installRequest(@RequestBody InstallRequestBean installRequestBean,
                                           @CookieValue(Constants.POSTGRES_DJANGO_SESSION_ID) String authToken) throws Exception {
        return getNextMenu(performInstall(installRequestBean, authToken));
    }

    @ApiOperation(value = "Update the application at the given reference")
    @RequestMapping(value = Constants.URL_UPDATE, method = RequestMethod.POST)
    @UserLock
    @UserRestore
    @AppInstall
    public BaseResponseBean updateRequest(@RequestBody UpdateRequestBean updateRequestBean,
                                          @CookieValue(Constants.POSTGRES_DJANGO_SESSION_ID) String authToken) throws Exception {
        MenuSession updatedSession = performUpdate(updateRequestBean, authToken);
        if (updateRequestBean.getSessionId() != null) {
            // Try restoring the old session, fail gracefully.
            try {
                FormSession oldSession = new FormSession(formSessionRepo.findOneWrapped(updateRequestBean.getSessionId()), restoreFactory);
                updatedSession.reloadSession(oldSession);
                return new NewFormResponse(oldSession);
            } catch (FormNotFoundException e) {
                log.info("FormSession with id " + updateRequestBean.getSessionId() + " not found, returning root");
            } catch (Exception e) {
                log.info("FormSession with id " + updateRequestBean.getSessionId()
                        + " failed to load with exception " + e);
            }
        }
        return getNextMenu(updatedSession);
    }

    @RequestMapping(value = Constants.URL_GET_DETAILS, method = RequestMethod.POST)
    @UserLock
    @UserRestore
    @AppInstall
    public EntityDetailListResponse getDetails(@RequestBody SessionNavigationBean sessionNavigationBean,
                                               @CookieValue(Constants.POSTGRES_DJANGO_SESSION_ID) String authToken) throws Exception {
        MenuSession menuSession;
        HqAuth auth = getAuthHeaders(
                sessionNavigationBean.getDomain(),
                sessionNavigationBean.getUsername(),
                authToken
        );
        try {
            menuSession = getMenuSessionFromBean(sessionNavigationBean, authToken);
        } catch (MenuNotFoundException e) {
            return null;
        }

        if (sessionNavigationBean.getIsPersistent()) {
            advanceSessionWithSelections(menuSession,
                    sessionNavigationBean.getSelections(),
                    auth,
                    null,
                    sessionNavigationBean.getQueryDictionary(),
                    sessionNavigationBean.getOffset(),
                    sessionNavigationBean.getSearchText(),
                    sessionNavigationBean.getSortIndex()
            );

            // See if we have a persistent case tile to expand
            EntityDetailListResponse detail = getInlineDetail(menuSession);
            if (detail == null) {
                throw new RuntimeException("Could not get inline details");
            }
            return detail;
        }

        String[] selections = sessionNavigationBean.getSelections();
        String[] commitSelections = new String[selections.length - 1];
        String detailSelection = selections[selections.length - 1];
        System.arraycopy(selections, 0, commitSelections, 0, selections.length - 1);

        advanceSessionWithSelections(
                menuSession,
                commitSelections,
                auth,
                detailSelection,
                sessionNavigationBean.getQueryDictionary(),
                sessionNavigationBean.getOffset(),
                sessionNavigationBean.getSearchText(),
                sessionNavigationBean.getSortIndex()
        );
        Screen currentScreen = menuSession.getNextScreen();

        if (!(currentScreen instanceof EntityScreen)) {
            // See if we have a persistent case tile to expand
            EntityDetailResponse detail = getPersistentDetail(menuSession);
            if (detail == null) {
                throw new RuntimeException("Tried to get details while not on a case list.");
            }
            return new EntityDetailListResponse(detail);
        }
        EntityScreen entityScreen = (EntityScreen) currentScreen;
        TreeReference reference = entityScreen.resolveTreeReference(detailSelection);

        if (reference == null) {
            throw new RuntimeException("Could not find case with ID " + detailSelection);
        }

        return new EntityDetailListResponse(
                entityScreen,
                menuSession.getSessionWrapper().getEvaluationContext(),
                reference
        );
    }

    /**
     * Make a a series of menu selections (as above, but can have multiple)
     *
     * @param sessionNavigationBean Give an installation code or path and a set of session selections
     * @param authToken             The Django session id auth token
     * @return A MenuBean or a NewFormResponse
     * @throws Exception
     */
    @RequestMapping(value = {Constants.URL_MENU_NAVIGATION, Constants.URL_INITIAL_MENU_NAVIGATION}, method = RequestMethod.POST)
    @UserLock
    @UserRestore
    @AppInstall
    public BaseResponseBean navigateSessionWithAuth(@RequestBody SessionNavigationBean sessionNavigationBean,
                                                    @CookieValue(Constants.POSTGRES_DJANGO_SESSION_ID) String authToken) throws Exception {
        HqAuth auth = getAuthHeaders(
                sessionNavigationBean.getDomain(),
                sessionNavigationBean.getUsername(),
                authToken
        );
        String[] selections = sessionNavigationBean.getSelections();
        MenuSession menuSession;
        CategoryTimingHelper.RecordingTimer timer = categoryTimingHelper.newTimer(Constants.TimingCategories.APP_INSTALL);
        timer.start();
        try {
            menuSession = getMenuSessionFromBean(sessionNavigationBean, authToken);
        } finally {
            timer.end()
                    .setMessage(timer.durationInMs() < 50 ? "Seems like nothing to install" : "This took some time")
                    .record();
        }
        BaseResponseBean response = advanceSessionWithSelections(
                menuSession,
                selections,
                auth,
                null,
                sessionNavigationBean.getQueryDictionary(),
                sessionNavigationBean.getOffset(),
                sessionNavigationBean.getSearchText(),
                sessionNavigationBean.getSortIndex()
        );
        // Don't update the menu session if we're using it already for navigation
        if (sessionNavigationBean.getMenuSessionId() == null || "".equals(sessionNavigationBean.getMenuSessionId())) {
            menuSessionRepo.save(new SerializableMenuSession(menuSession));
        }
        return response;
    }

    private MenuSession getMenuSessionFromBean(SessionNavigationBean sessionNavigationBean, String authToken) throws Exception {
        MenuSession menuSession = null;
        String menuSessionId = sessionNavigationBean.getMenuSessionId();
        if (menuSessionId != null && !"".equals(menuSessionId)) {
            menuSession = getMenuSession(
                    sessionNavigationBean.getDomain(),
                    sessionNavigationBean.getUsername(),
                    menuSessionId,
                    authToken
            );
        } else {
            // If we have a preview command, load that up
            if (sessionNavigationBean.getPreviewCommand() != null) {
                menuSession = handlePreviewCommand(sessionNavigationBean, authToken);
            } else {
                menuSession = performInstall(sessionNavigationBean, authToken);
            }
        }
        return menuSession;
    }

    /**
     * Advances the session based on the selections.
     *
     * @param menuSession
     * @param selections      - Selections are either an integer index into a list of modules
     *                        or a case id indicating the case selected for a case detail.
     *                        <p>
     *                        An example selection would be ["0", "2", "6c5d91e9-61a2-4264-97f3-5d68636ff316"]
     *                        <p>
     *                        This would mean select the 0th menu, then the 2nd menu, then the case with the id 6c5d91e9-61a2-4264-97f3-5d68636ff316.
     * @param auth
     * @param detailSelection - If requesting a case detail will be a case id, else null. When the case id is given
     *                        it is used to short circuit the normal TreeReference calculation by inserting a predicate that
     *                        is [@case_id = <detailSelection>].
     * @param queryDictionary
     * @param offset
     * @param searchText
     */
    private BaseResponseBean advanceSessionWithSelections(MenuSession menuSession,
                                                          String[] selections,
                                                          HqAuth auth,
                                                          String detailSelection,
                                                          Hashtable<String, String> queryDictionary,
                                                          int offset,
                                                          String searchText,
                                                          int sortIndex) throws Exception {
        BaseResponseBean nextMenu;
        // If we have no selections, we're are the root screen.
        if (selections == null) {
            nextMenu = getNextMenu(
                    menuSession,
                    offset,
                    searchText,
                    sortIndex
            );
            return nextMenu;
        }

        String[] overrideSelections = null;
        NotificationMessage notificationMessage = new NotificationMessage();
        for (int i = 1; i <= selections.length; i++) {
            String selection = selections[i - 1];
            boolean gotNextScreen = menuSession.handleInput(selection);
            if (!gotNextScreen) {
                notificationMessage = new NotificationMessage(
                        "Overflowed selections with selection " + selection + " at index " + i, (true));
                break;
            }
            Screen nextScreen = menuSession.getNextScreen();

            if (nextScreen instanceof FormplayerQueryScreen && queryDictionary != null) {
                notificationMessage = doQuery(
                        (FormplayerQueryScreen) nextScreen,
                        menuSession,
                        queryDictionary
                );
                overrideSelections = trimCaseClaimSelections(selections);
            }
            if (nextScreen instanceof FormplayerSyncScreen) {
                BaseResponseBean syncResponse = doSyncGetNext(
                        (FormplayerSyncScreen) nextScreen,
                        menuSession,
                        auth);
                if (syncResponse != null) {
                    syncResponse.setSelections(overrideSelections);
                    return syncResponse;
                }
            }
        }



        nextMenu = getNextMenu(
                menuSession,
                detailSelection,
                offset,
                searchText,
                sortIndex
        );
        if (nextMenu != null) {
            nextMenu.setNotification(notificationMessage);
            nextMenu.setSelections(overrideSelections);
            log.info("Returning menu: " + nextMenu);
            return nextMenu;
        } else {
            BaseResponseBean responseBean = resolveFormGetNext(menuSession);
            if (responseBean == null) {
                responseBean = new BaseResponseBean(null, "Got null menu, redirecting to home screen.", false, true);
            }
            responseBean.setSelections(overrideSelections);
            return responseBean;
        }
    }

    private MenuSession handlePreviewCommand(SessionNavigationBean sessionNavigationBean, String authToken) throws Exception {
        MenuSession menuSession;
        // When previewing, clear and reinstall DBs to get newest version
        // Big TODO: app updates
        new ApplicationDB(
                sessionNavigationBean.getDomain(),
                sessionNavigationBean.getUsername(),
                sessionNavigationBean.getRestoreAs(),
                sessionNavigationBean.getAppId()
        ).deleteDatabaseFolder();
        menuSession = performInstall(sessionNavigationBean, authToken);
        try {
            menuSession.getSessionWrapper().setCommand(sessionNavigationBean.getPreviewCommand());
            menuSession.updateScreen();
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("Couldn't get entries from preview command "
                    + sessionNavigationBean.getPreviewCommand() + ". If this error persists" +
                    " please report a bug to the CommCareHQ Team.");
        }
        return menuSession;
    }

    /**
     * If we've encountered a QueryScreen and have a QueryDictionary, do the query
     * and update the session, screen, and notification message accordingly.
     * <p>
     * Will do nothing if this wasn't a query screen.
     */
    private NotificationMessage doQuery(FormplayerQueryScreen screen,
                                        MenuSession menuSession,
                                        Hashtable<String, String> queryDictionary) throws CommCareSessionException {
        log.info("Formplayer doing query with dictionary " + queryDictionary);
        NotificationMessage notificationMessage = null;
        screen.answerPrompts(queryDictionary);
        String responseString = queryRequester.makeQueryRequest(screen.getUriString(), screen.getAuthHeaders());
        boolean success = screen.processSuccess(new ByteArrayInputStream(responseString.getBytes(StandardCharsets.UTF_8)));
        if (success) {
            if (screen.getCurrentMessage() != null) {
                notificationMessage = new NotificationMessage(screen.getCurrentMessage(), false);
            }
        } else {
            notificationMessage = new NotificationMessage("Query failed with message " + screen.getCurrentMessage(), true);
        }
        menuSession.updateScreen();
        Screen nextScreen = menuSession.getNextScreen();
        log.info("Next screen after query: " + nextScreen);
        return notificationMessage;
    }

    /**
     * Perform the sync and update the notification and screen accordingly.
     * After a sync, we can either pop another menu/form to begin
     * or just return to the app menu.
     */
    private BaseResponseBean doSyncGetNext(FormplayerSyncScreen nextScreen,
                                           MenuSession menuSession,
                                           HqAuth auth) throws Exception {
        NotificationMessage notificationMessage = doSync(
                nextScreen,
                auth
        );

        BaseResponseBean postSyncResponse = resolveFormGetNext(menuSession);
        if (postSyncResponse != null) {
            // If not null, we have a form or menu to redirect to
            postSyncResponse.setNotification(notificationMessage);
            return postSyncResponse;
        } else {
            // Otherwise, return use to the app root
            postSyncResponse = new BaseResponseBean(null, "Redirecting after sync", false, true);
            postSyncResponse.setNotification(notificationMessage);
            return postSyncResponse;
        }
    }

    private NotificationMessage doSync(FormplayerSyncScreen screen, HqAuth auth) throws Exception {
        ResponseEntity<String> responseEntity = syncRequester.makeSyncRequest(screen.getUrl(),
                screen.getBuiltQuery(),
                auth.getAuthHeaders());
        if (responseEntity == null) {
            return new NotificationMessage("Session error, expected sync block but didn't get one.", true);
        }
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            CaseAPIs.performSync(restoreFactory);
            return new NotificationMessage("Case claim successful.", false);
        } else {
            return new NotificationMessage(
                    String.format("Case claim failed. Message: %s", responseEntity.getBody()), true);
        }
    }

    private String[] trimCaseClaimSelections(String[] selections) {
        String actionSelections = selections[selections.length - 2];
        if (!actionSelections.contains("action")) {
            log.error(String.format("Selections %s did not contain expected action at position %s.",
                    Arrays.toString(selections),
                    selections[selections.length - 2]));
            return selections;
        }
        String[] newSelections = new String[selections.length - 1];
        System.arraycopy(selections, 0, newSelections, 0, selections.length - 2);
        newSelections[selections.length - 2] = selections[selections.length - 1];
        return newSelections;
    }

    private MenuSession performInstall(InstallRequestBean bean, String authToken) throws Exception {
        HqAuth auth = getAuthHeaders(
                bean.getDomain(),
                bean.getUsername(),
                authToken
        );
        if ((bean.getAppId() == null || "".equals(bean.getAppId())) &&
                bean.getInstallReference() == null || "".equals(bean.getInstallReference())) {
            throw new RuntimeException("Either app_id or installReference must be non-null.");
        }

        return new MenuSession(
                bean.getUsername(),
                bean.getDomain(),
                bean.getAppId(),
                bean.getInstallReference(),
                bean.getLocale(),
                installService,
                restoreFactory,
                auth,
                host,
                bean.getOneQuestionPerScreen(),
                bean.getRestoreAs()
        );
    }

    private MenuSession performUpdate(UpdateRequestBean updateRequestBean, String authToken) throws Exception {
        MenuSession currentSession = performInstall(updateRequestBean, authToken);
        currentSession.updateApp(updateRequestBean.getUpdateMode());
        return currentSession;
    }
}
