package lsfusion.server.logics.property.actions.integration.importing.hierarchy.json;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Iterator;

public class JSONReader {

    public static Object readRootObject(byte[] file, String root) throws IOException, JSONException {
        Object rootNode = JSONReader.readObject(file);
        if (root != null) {
            rootNode = JSONReader.findRootNode(rootNode, null, root);
            if (rootNode == null)
                throw new RuntimeException(String.format("Import JSON error: root node %s not found", root));
        }
        return rootNode;
    }


    public static Object readObject(byte[] file) throws IOException, JSONException {
        try (InputStream is = new ByteArrayInputStream(file)) {
            final BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            final String jsonText = readAll(rd).trim();

            if(jsonText.startsWith("["))
                return new JSONArray(jsonText);
            if(jsonText.startsWith("{"))
                return new JSONObject(jsonText);
            return jsonText;
        }
    }

    private static String readAll(final Reader rd) throws IOException {
        final StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public static Object findRootNode(Object rootNode, String rootName, String root) throws JSONException {
        if (rootNode instanceof JSONArray) {
            JSONArray array = (JSONArray) rootNode;
            for (int i = 0; i < array.length(); i++) {
                Object result = findRootNode(array.get(i), null, root);
                if (result != null)
                    return result;
            }

        } else if (rootNode instanceof JSONObject) {

            Iterator<String> it = ((JSONObject) rootNode).keys();
            while (it.hasNext()) {
                String key = it.next();
                Object child = ((JSONObject) rootNode).get(key);
                Object result = findRootNode(child, key, root);
                if(result != null)
                    return result;
            }            
        } else if (rootName != null && rootName.equals(root))
                return rootNode;
        return null;
    }

    public static JSONObject toJSONObject(Object object, boolean convertValue) throws JSONException {
        if(object instanceof JSONObject)
            return (JSONObject) object;
        
        JSONObject virtObject = new JSONObject();
        virtObject.put("value", object);
        return virtObject;
    }

    public static Object fromJSONObject(JSONObject object, boolean convertValue) throws JSONException {
        Iterator keys = object.keys();
        if(keys.hasNext()) {
            String next = (String)keys.next();
            if(!keys.hasNext() && next.equals("value"))
                return object.get(next);
        }
        return object;
    }
}