package beans.menus;

import org.commcare.modern.session.SessionWrapper;
import org.commcare.suite.model.DisplayUnit;
import org.commcare.suite.model.MenuDisplayable;
import org.commcare.util.cli.MenuScreen;
import org.commcare.util.cli.QueryScreen;

import java.util.*;

/**
 * Created by willpride on 4/13/16.
 */
public class QueryResponseBean extends MenuBean {

    private DisplayElement[] displays;
    private final String type = "query";

    QueryResponseBean(){}

    public DisplayElement[] getDisplays() {
        return displays;
    }

    private void setDisplays(DisplayElement[] displays) {
        this.displays = displays;
    }

    public QueryResponseBean(QueryScreen queryScreen, SessionWrapper session){
        Hashtable<String, DisplayUnit> displayMap = queryScreen.getUserInputDisplays();
        displays = new DisplayElement[displayMap.size()];
        int count = 0 ;
        for (Map.Entry<String, DisplayUnit> displayEntry : displayMap.entrySet()) {
            displays[count] = new DisplayElement(displayEntry.getValue(), session.getEvaluationContext());
            count++;
        }
        setTitle(queryScreen.getScreenTitle());
    }

    @Override
    public String toString(){
        return "QueryResponseBean [displays=" + Arrays.toString(displays)
                + "MenuBean= " + super.toString() + "]";
    }

    public String getType() {
        return type;
    }
}
