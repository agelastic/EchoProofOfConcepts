package jo.jose.logic;

import java.io.IOException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletRequest;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SimpleCard;

public class JoseAppLogic
{
    public static SpeechletResponse interact(SpeechletRequest request, Session session)
    {
        try
        {
            JSONObject user = getUser(session);
            if (request instanceof IntentRequest)
                return doIntent(request, user);
            else
                return doWelcome(request, user);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return buildSpeechletResponse("Something went wrong. "+e.getLocalizedMessage());
        }
    }
    private static SpeechletResponse doWelcome(SpeechletRequest request, JSONObject user)
    {
        return buildSpeechletResponse("Welcome to Jose Fabuloso. "
                + "You've read the book, now play the game! "
                + "You are currently at "+UserLogic.getLocationName(user)+", and have "+UserLogic.getHoldSpace(user)+" tons of space free in your hold.");
    }
    private static SpeechletResponse doIntent(SpeechletRequest request, JSONObject user) throws IOException
    {
        Intent intent = ((IntentRequest)request).getIntent();
        String response = "I'm not quite sure what you want.";
        if (intent != null)
        {
            String verb = intent.getName();
            if (verb.equals("LOCATION"))
                response = doLocation(intent, user);
            else if (verb.equals("JUMP"))
                response = doJump(intent, user);
            else if (verb.equals("RESET"))
                response = doReset(intent, user);
            else if (verb.equals("SPACE"))
                response = doSpace(intent, user);
            else if (verb.equals("MONEY"))
                response = doMoney(intent, user);
            else if (verb.equals("NEARBY"))
                response = doNearby(intent, user);
            else if (verb.equals("DATE"))
                response = doDate(intent, user);
            else if (verb.equals("FORSALE"))
                response = doForSale(intent, user);
        }
        return buildSpeechletResponse(response);
    }
    private static String doReset(Intent intent, JSONObject user) throws IOException
    {
        user = UserLogic.doNewUser(UserLogic.getName(user));
        return "It was all a bad dream... "+doLocation(intent, user);
    }
    private static String doDate(Intent intent, JSONObject user) throws IOException
    {
        int year = UserLogic.getYear(user);
        int day = UserLogic.getDay(user);
        return "It is day "+day+" of year "+year+".";
    }
    private static String doSpace(Intent intent, JSONObject user) throws IOException
    {
        return "You have "+UserLogic.getHoldSpace(user)+" tons of space free in your hold.";
    }
    private static String doMoney(Intent intent, JSONObject user) throws IOException
    {
        return "You have "+UserLogic.getMoney(user)+" talents.";
    }
    private static String doNearby(Intent intent, JSONObject user) throws IOException
    {
        JSONObject ports = PortsLogic.getNearbyPorts(UserLogic.getLocation(user), UserLogic.getJump(user));
        String atName = UserLogic.getLocationName(user);
        JSONObject at = PortsLogic.findPort(ports, atName);
        StringBuffer sb = new StringBuffer();
        for (Object p : PortsLogic.getPorts(ports))
        {
            JSONObject port = (JSONObject)p;
            String pName = PortLogic.getName(port);
            if (pName.equals(atName))
                continue;
            String dist = PortLogic.distanceDescription(port, at);
            sb.append(pName+" is "+dist+" away. ");
        }
        return sb.toString();
    }
    private static String doLocation(Intent intent, JSONObject user) throws IOException
    {
        JSONObject port = resolveLocation(intent, user);
        if (port == null)
            if (intent.getSlot("location") != null)
                return "I'm not quite sure where you mean.";
            else
                port = PortLogic.lookup(UserLogic.getLocation(user));
        String resp = "The port of "+PortLogic.getName(port)
                +" has a population of "+PortLogic.getPopulationDescription(port)
                +", a tech tier of "+PortLogic.getTechTierDescription(port)
                +", and an economy that mostly focuses on "+PortLogic.getProductionFocus(port)
                +".";
        
        return resp;
    }    
    private static String doJump(Intent intent, JSONObject user) throws IOException
    {
        JSONObject port = resolveLocation(intent, user);
        if (port == null)
            if (intent.getSlot("location") != null)
                return "I'm not quite sure where you mean. Say 'nearby' to see nearby worlds.";
            else
                return "Say 'jump world'. To get a list of nearby worlds, say 'nearby'.";
        String loc = PortLogic.getID(port);
        UserLogic.setLocation(user, loc);
        return "You have arrived at "+UserLogic.getLocationName(user)+".";
    }    
    private static String doForSale(Intent intent, JSONObject user) throws IOException
    {
        JSONObject ondock = OnDockLogic.queryOnDock(user);
        Slot slot = intent.getSlot("location");
        if (slot == null)
            return listLots(OnDockLogic.getFilteredLots(ondock, user));
        else if ("everything".equals(slot.getValue()) || "all".equals(slot.getValue()))
            return listLots(OnDockLogic.getLots(ondock));
        JSONObject lot = resolveLot(intent, user, ondock);
        if (lot == null)
            return "I'm not quite sure where you mean. Say 'dock' to see everything for sale.";
        String name = OnDockLogic.getName(lot);
        int size = OnDockLogic.getSize(lot);
        int price = OnDockLogic.getPurchasePrice(lot);
        return size+" tons of "+name+" for "+price+" talents. ";
    }    
    
    private static String listLots(JSONArray lots)
    {
        StringBuffer sb = new StringBuffer();
        for (Object o : lots)
        {
            JSONObject lot = (JSONObject)o;
            String name = OnDockLogic.getName(lot);
            int size = OnDockLogic.getSize(lot);
            int price = OnDockLogic.getPurchasePrice(lot);
            sb.append(size+" tons of "+name+" for "+price+" talents. ");
        }
        return sb.toString();
    }
    private static JSONObject resolveLot(Intent intent, JSONObject user, JSONObject ondock) throws IOException
    {
        Slot slot = intent.getSlot("lotnum");
        if (slot == null)
            return null;
        String lotName = slot.getValue();
        if ((lotName == null) || (lotName.length() == 0))
            return null;
        JSONObject lot = OnDockLogic.findLot(OnDockLogic.getFilteredLots(ondock, user), lotName);
        return lot;
    }
    private static JSONObject resolveLocation(Intent intent, JSONObject user) throws IOException
    {
        Slot slot = intent.getSlot("location");
        if (slot == null)
            return null;
        String locationName = slot.getValue();
        if ((locationName == null) || (locationName.length() == 0))
            return null;
        JSONObject ports = PortsLogic.getNearbyPorts(UserLogic.getLocation(user), UserLogic.getJump(user));
        return PortsLogic.findPort(ports, locationName);
    }
    
    private static JSONObject getUser(Session session) throws IOException
    {
        String id = getUserID(session);
        JSONObject user = UserLogic.getUser(id);
        if (user == null)
            user = UserLogic.doNewUser(id);
        return user;
    }
    private static String getUserID(Session session)
    {
        String id = session.getSessionId();
        if (session.getUser() != null)
            if (session.getUser().getUserId() != null)
                id = session.getUser().getUserId();
        return id;
    }
    /**
     * Creates and returns the visual and spoken response with shouldEndSession flag.
     *
     * @param title
     *            title for the companion application home card
     * @param output
     *            output content for speech and companion application home card
     * @param shouldEndSession
     *            should the session be closed
     * @return SpeechletResponse spoken and visual response for the given input
     */
    private static SpeechletResponse buildSpeechletResponse(String output) {
        String title = "Grocery List";
        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle(title);
        card.setContent(output);
        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(output);
        // Create the speechlet response.
        SpeechletResponse response = new SpeechletResponse();
        response.setShouldEndSession(false);
        response.setOutputSpeech(speech);
        response.setCard(card);
        return response;
    }
}