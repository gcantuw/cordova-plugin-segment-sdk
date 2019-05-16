package com.segment.analytics.cordova;

import android.util.Log;
// 
import com.segment.analytics.Analytics;
import com.segment.analytics.Analytics.LogLevel;
import com.segment.analytics.AnalyticsContext;
import com.segment.analytics.AnalyticsContext.Campaign;
import com.segment.analytics.Properties;
import com.segment.analytics.Properties.Product;
import com.segment.analytics.StatsSnapshot;
import com.segment.analytics.Traits;
import com.segment.analytics.Traits.Address;

// import org.apache.cordova.BuildConfig;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;



public class SegmentPlugin extends CordovaPlugin {

    private static final String TAG = "SegmentPlugin";

    // static to avoid potential duplicates
    private static Analytics analytics;

    /*
    *
    * HELPERS
    *
    */

    private LogLevel _getLogLevel() {
        return LogLevel.VERBOSE;
    }

    private String _getAnalyticsKey() {
        return this.preferences.getString("android_segment_write_key", null);
    }

    // pure function used by concurrent thread
    private Analytics _getAnalyticsInstance(String writeKey, LogLevel logLevel) {

        Analytics a;

        if (writeKey == null || "".equals(writeKey)) {
            a = null;
            Log.e(TAG, "Invalid Segment write key: " + writeKey);
        } else {
            a = new Analytics.Builder(
                cordova.getActivity().getApplicationContext(),
                writeKey
            )
            .logLevel(logLevel)
            // .trackApplicationLifecycleEvents()
            .build();

        }
        return a;
    }


    /*
    *
    * PLUGIN METHODS
    *
    */

    @Override protected void pluginInitialize() {

        if (SegmentPlugin.analytics != null) {
            Log.d(TAG, "Analytics instance already exists!");
            return;
        }

        // get Analytics instance if it's not already initialised
        final String writeKey = this._getAnalyticsKey();
        final LogLevel logLevel = this._getLogLevel();
        final SegmentPlugin plugin = this;

        // using a thread pool so as not to block the main thread
        Future<Analytics> future = cordova.getThreadPool().submit(new Callable<Analytics>() {
            @Override
            public Analytics call() throws Exception {
                return plugin._getAnalyticsInstance(writeKey, logLevel);
            }
        });

        try {
            SegmentPlugin.analytics = future.get();
            Log.d(TAG, "SegmentPlugin.analytics " + SegmentPlugin.analytics.toString());
            Analytics.setSingletonInstance(SegmentPlugin.analytics);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted error");
            e.printStackTrace();
        } catch (ExecutionException e) {
            Log.e(TAG, "Execution error");
            e.printStackTrace();
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (SegmentPlugin.analytics == null) {
            Log.e(TAG, "Error initializing");
            return false;
        }

        if ("identify".equals(action)) {
            identify(args);
            return true;
        } else if ("group".equals(action)) {
            group(args);
            return true;
        } else if ("track".equals(action)) {
            track(args);
            return true;
        } else if ("screen".equals(action)) {
            screen(args);
            return true;
        } else if ("alias".equals(action)) {
            alias(args);
            return true;
        } else if ("reset".equals(action)) {
            reset();
            return true;
        } else if ("flush".equals(action)) {
            flush();
            return true;
        } else if ("getAnonymousId".equals(action)) {
            getAnonymousId(callbackContext);
            return true;
        } else if ("getSnapshot".equals(action)) {
            getSnapshot(callbackContext);
            return true;
        }

        return false;
    }


    /*
    *
    * SEGMENT METHODS
    *
    */

    private void identify(final JSONArray args) {

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {

                JSONObject contextObj = args.optJSONObject(2);

                AnalyticsContext analyticsContext = analytics.getAnalyticsContext();
                analyticsContext = enrichAnalyticsContext(analyticsContext, contextObj);

                Log.d(TAG, "Segment - Identify - ContextObj: " + contextObj.toString());

                analytics.with(cordova.getActivity().getApplicationContext()).identify(
                        optArgString(args, 0),
                        makeTraitsFromJSON(args.optJSONObject(1)),
                        null // passing options is deprecated
                );
            }
        });
    }

    private void group(JSONArray args) {

        analytics.with(cordova.getActivity().getApplicationContext()).group(
                optArgString(args, 0),
                makeTraitsFromJSON(args.optJSONObject(1)),
                null // passing options is deprecated
        );
    }

    private void track(JSONArray args) {

        JSONObject contextObj = args.optJSONObject(2);

        AnalyticsContext analyticsContext = analytics.getAnalyticsContext();
        analyticsContext = enrichAnalyticsContext(analyticsContext, contextObj);

        Log.d(TAG, "Segment - Track - ContextObj: " + contextObj.toString());

        analytics.with(cordova.getActivity().getApplicationContext()).track(
                optArgString(args, 0),
                makePropertiesFromJSON(args.optJSONObject(1)),
                null // passing options is deprecated
        );
    }

    private void screen(JSONArray args) {

        JSONObject contextObj = args.optJSONObject(2);

        AnalyticsContext analyticsContext = analytics.getAnalyticsContext();
        analyticsContext = enrichAnalyticsContext(analyticsContext, contextObj);

        //Log.d(TAG, "Segment - Screen - ContextObj: " + contextObj.toString());

        Log.d(TAG, "optArgString(args, 0) " + optArgString(args, 0));

        //Analytics.with(context).screen("Page", pageName);

        analytics.with(cordova.getActivity().getApplicationContext()).screen(
                optArgString(args, 0)
        );
    }

    private void alias(JSONArray args) {

        analytics.with(cordova.getActivity().getApplicationContext()).alias(
                optArgString(args, 0)
        );
    }

    private void reset() {
        analytics.with(cordova.getActivity().getApplicationContext()).reset();
    }

    private void flush() {
        analytics.with(cordova.getActivity().getApplicationContext()).flush();
    }

    private void getAnonymousId(CallbackContext callbackContext) {

        Traits traits = new Traits();
        traits = analytics.getAnalyticsContext().traits();
        Map<String, Object> traitMap = traits;
        JSONObject snapshotJSON = new JSONObject();

        try {
            snapshotJSON.put("anonymousId", traitMap.get("anonymousId"));
            PluginResult r = new PluginResult(PluginResult.Status.OK, snapshotJSON);
            r.setKeepCallback(false);
            callbackContext.sendPluginResult(r);
        } catch(JSONException e) {
            e.printStackTrace();
            return;
        }
    }

    private void getSnapshot(CallbackContext callbackContext) {

        StatsSnapshot snapshot = analytics.with(cordova.getActivity().getApplicationContext()).getSnapshot();
        JSONObject snapshotJSON = new JSONObject();

        try {
            snapshotJSON.put("timestamp", snapshot.timestamp);
            snapshotJSON.put("flushCount", snapshot.flushCount);
            snapshotJSON.put("flushEventCount", snapshot.flushEventCount);
            snapshotJSON.put("integrationOperationCount", snapshot.integrationOperationCount);
            snapshotJSON.put("integrationOperationDuration", snapshot.integrationOperationDuration);
            snapshotJSON.put("integrationOperationAverageDuration", snapshot.integrationOperationAverageDuration);
            snapshotJSON.put("integrationOperationDurationByIntegration", new JSONObject(snapshot.integrationOperationDurationByIntegration));

            PluginResult r = new PluginResult(PluginResult.Status.OK, snapshotJSON);
            r.setKeepCallback(false);
            callbackContext.sendPluginResult(r);
        } catch(JSONException e) {
            e.printStackTrace();
            return;
        }
    }

    /*
    *
    * SEGMENT HELPER METHODS
    *
    */

    private Traits makeTraitsFromJSON(JSONObject json) {

        Traits traits = new Traits();
        Map<String, Object> traitMap = mapFromJSON(json);

        if (traitMap != null) {
            if (traitMap.get("address") != null) {
                traitMap.put("address", new Address((Map<String, Object>) traitMap.get("address")));
            }

            traits.putAll(traitMap);
        }

        return traits;
    }

    private Properties makePropertiesFromJSON(JSONObject json) {

        Properties properties = new Properties();
        Map<String, Object> propertiesMap = mapFromJSON(json);

        if (propertiesMap != null) {
            List<Map<String, Object>> rawProducts = (List<Map<String, Object>>) propertiesMap.get("products");

            if (rawProducts != null) {
                List<Product> products = new ArrayList<Product>();

                for (Map<String, Object> rawProduct : rawProducts) {
                    Product product = new Product(
                        rawProduct.get("id") == null ? "" : (String) rawProduct.get("id"),
                        rawProduct.get("sku") == null ? "" : (String) rawProduct.get("sku"),
                        rawProduct.get("price") == null ? 0d : Double.valueOf(rawProduct.get("price").toString())
                    );

                    product.putAll(rawProduct);
                    products.add(product);
                }

                propertiesMap.put("products", products.toArray(new Product[products.size()]));
            }

            properties.putAll(propertiesMap);
        }

        return properties;
    }

    private AnalyticsContext enrichAnalyticsContext(AnalyticsContext analyticsContext, JSONObject json) {

        Campaign campaign = makeCampaignFromJSON(json);
        analyticsContext.putCampaign(campaign);
        
        return analyticsContext;
    }

    private Campaign makeCampaignFromJSON(JSONObject json) {

        Map<String, Object> objMap = mapFromJSON(json);
        Campaign campaign = new Campaign();

        if (objMap != null && false) {

            Map<String, Object> contextMap = (Map<String, Object>) objMap.get("context");
            Map<String, Object> campaignMap = (Map<String, Object>) contextMap.get("campaign");

            if (campaignMap != null) {
                String cmpName = (String) campaignMap.get("name");
                String cmpSource = (String) campaignMap.get("source");
                String cmpMedium = (String) campaignMap.get("medium");
                String cmpContent = (String) campaignMap.get("content");
                String cmpTerm = (String) campaignMap.get("term");

                if (cmpName != null) {
                    campaign.putName(cmpName);
                }

                if (cmpSource != null) {
                    campaign.putSource(cmpSource);
                }

                if (cmpMedium != null) {
                    campaign.putMedium(cmpMedium);
                }

                if (cmpContent != null) {
                    campaign.putContent(cmpContent);
                }

                if (cmpTerm != null) {
                    campaign.putTerm(cmpTerm);
                }
            }
        }

        return campaign;
    }

    private static Map<String, Object> mapFromJSON(JSONObject jsonObject) {

        if (jsonObject == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        Iterator<String> keysIter = jsonObject.keys();
        while (keysIter.hasNext()) {
            String key = keysIter.next();
            Object value = jsonObject.isNull(key) ? null : getObject(jsonObject.opt(key));

            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static List<Object> listFromJSON(JSONArray jsonArray) {

        List<Object> list = new ArrayList<Object>();
        for (int i = 0, count = jsonArray.length(); i < count; i++) {
            Object value = getObject(jsonArray.opt(i));
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    private static Object getObject(Object value) {

        if (value instanceof JSONObject) {
            value = mapFromJSON((JSONObject) value);
        } else if (value instanceof JSONArray) {
            value = listFromJSON((JSONArray) value);
        }
        return value;
    }

    public static String optArgString(JSONArray args, int index)
    {
        return args.isNull(index) ? null : args.optString(index);
    }

}
