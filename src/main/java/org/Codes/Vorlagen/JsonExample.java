package communication.socket;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * @see <a href="https://github.com/google/gson">Gson Project</a>
 */
@SuppressWarnings("unchecked")
public class JsonExample {

    public static void main(String[] args) {

        Gson gson = new Gson();

        Map<String, String> map = new HashMap<>();
        map.put("name", "jon doe");
        map.put("age", "22");
        map.put("city", "chicago");

        System.out.format("HashMap: %s\n", map); // Initial map
        String json = gson.toJson(map);      // ...convert to JSON
        System.out.format("JSON: %s\n\n", json);

        Map<String, String> newMap;
        newMap = gson.fromJson(json, HashMap.class); // ...back to a new HashMap
        System.out.format("HashMap: %s\n", newMap);
    }
}
