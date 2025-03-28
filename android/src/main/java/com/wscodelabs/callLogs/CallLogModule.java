package com.wscodelabs.callLogs;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import com.facebook.react.bridge.*;
import org.json.JSONArray;
import org.json.JSONException;

import javax.annotation.Nullable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class CallLogModule extends ReactContextBaseJavaModule {

    private Context context;

    public CallLogModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
    }

    @Override
    public String getName() {
        return "CallLogs";
    }

    @ReactMethod
    public void loadAll(Promise promise) {
        load(-1, promise);
    }

    @ReactMethod
    public void load(int limit, Promise promise) {
        loadWithFilter(limit, null, promise);
    }

    @ReactMethod
    public void loadWithFilter(int limit, @Nullable ReadableMap filter, Promise promise) {
        try {
            String[] projection;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                projection = new String[]{
                        Calls.NUMBER,
                        Calls.TYPE,
                        Calls.DATE,
                        Calls.DURATION,
                        Calls.CACHED_NAME,
                        "subscription_id"
                };
            } else {
                projection = new String[]{
                        Calls.NUMBER,
                        Calls.TYPE,
                        Calls.DATE,
                        Calls.DURATION,
                        Calls.CACHED_NAME
                };
            }

            Cursor cursor = this.context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null, null,
                    CallLog.Calls.DATE + " DESC");

            WritableArray result = Arguments.createArray();

            if (cursor == null) {
                promise.resolve(result);
                return;
            }

            boolean nullFilter = filter == null;
            String minTimestamp = !nullFilter && filter.hasKey("minTimestamp") ? filter.getString("minTimestamp") : "0";
            String maxTimestamp = !nullFilter && filter.hasKey("maxTimestamp") ? filter.getString("maxTimestamp") : "-1";

            String types = !nullFilter && filter.hasKey("types") ? filter.getString("types") : "[]";
            JSONArray typesArray = new JSONArray(types);
            Set<String> typeSet = new HashSet<>(Arrays.asList(toStringArray(typesArray)));

            String phoneNumbers = !nullFilter && filter.hasKey("phoneNumbers") ? filter.getString("phoneNumbers") : "[]";
            JSONArray phoneNumbersArray = new JSONArray(phoneNumbers);
            Set<String> phoneNumberSet = new HashSet<>(Arrays.asList(toStringArray(phoneNumbersArray)));

            int callLogCount = 0;

            final int NUMBER_COLUMN_INDEX = cursor.getColumnIndex(Calls.NUMBER);
            final int TYPE_COLUMN_INDEX = cursor.getColumnIndex(Calls.TYPE);
            final int DATE_COLUMN_INDEX = cursor.getColumnIndex(Calls.DATE);
            final int DURATION_COLUMN_INDEX = cursor.getColumnIndex(Calls.DURATION);
            final int NAME_COLUMN_INDEX = cursor.getColumnIndex(Calls.CACHED_NAME);
            final int SUBSCRIPTION_ID_INDEX = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? cursor.getColumnIndex("subscription_id") : -1;

            boolean minTimestampDefined = minTimestamp != null && !minTimestamp.equals("0");
            boolean minTimestampReached = false;

            while (cursor.moveToNext() && this.shouldContinue(limit, callLogCount) && !minTimestampReached) {
                String phoneNumber = cursor.getString(NUMBER_COLUMN_INDEX);
                int duration = cursor.getInt(DURATION_COLUMN_INDEX);
                String name = cursor.getString(NAME_COLUMN_INDEX);

                String timestampStr = cursor.getString(DATE_COLUMN_INDEX);
                minTimestampReached = minTimestampDefined && Long.parseLong(timestampStr) <= Long.parseLong(minTimestamp);

                DateFormat df = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM);
                String dateTime = df.format(new Date(Long.parseLong(timestampStr)));

                String type = this.resolveCallType(cursor.getInt(TYPE_COLUMN_INDEX));

                boolean passesPhoneFilter = phoneNumberSet == null || phoneNumberSet.isEmpty() || phoneNumberSet.contains(phoneNumber);
                boolean passesTypeFilter = typeSet == null || typeSet.isEmpty() || typeSet.contains(type);
                boolean passesMinTimestampFilter = minTimestamp == null || minTimestamp.equals("0") || Long.parseLong(timestampStr) >= Long.parseLong(minTimestamp);
                boolean passesMaxTimestampFilter = maxTimestamp == null || maxTimestamp.equals("-1") || Long.parseLong(timestampStr) <= Long.parseLong(maxTimestamp);
                boolean passesFilter = passesPhoneFilter && passesTypeFilter && passesMinTimestampFilter && passesMaxTimestampFilter;

                if (passesFilter) {
                    WritableMap callLog = Arguments.createMap();
                    callLog.putString("phoneNumber", phoneNumber);
                    callLog.putInt("duration", duration);
                    callLog.putString("name", name);
                    callLog.putString("timestamp", timestampStr);
                    callLog.putString("dateTime", dateTime);
                    callLog.putString("type", type);
                    callLog.putInt("rawType", cursor.getInt(TYPE_COLUMN_INDEX));

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && SUBSCRIPTION_ID_INDEX != -1) {
                        int subscriptionId = cursor.getInt(SUBSCRIPTION_ID_INDEX);
                        callLog.putInt("subscriptionId", subscriptionId);
                    } else {
                        callLog.putInt("subscriptionId", -1);
                    }

                    result.pushMap(callLog);
                    callLogCount++;
                }
            }

            cursor.close();
            promise.resolve(result);
        } catch (JSONException e) {
            promise.reject(e);
        }
    }


    public static String[] toStringArray(JSONArray array) {
        if (array == null)
            return null;

        String[] arr = new String[array.length()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = array.optString(i);
        }
        return arr;
    }

    private String resolveCallType(int callTypeCode) {
        switch (callTypeCode) {
            case Calls.OUTGOING_TYPE:
                return "OUTGOING";
            case Calls.INCOMING_TYPE:
                return "INCOMING";
            case Calls.MISSED_TYPE:
                return "MISSED";
            case Calls.VOICEMAIL_TYPE:
                return "VOICEMAIL";
            case Calls.REJECTED_TYPE:
                return "REJECTED";
            case Calls.BLOCKED_TYPE:
                return "BLOCKED";
            case Calls.ANSWERED_EXTERNALLY_TYPE:
                return "ANSWERED_EXTERNALLY";
            default:
                return "UNKNOWN";
        }
    }

    private boolean shouldContinue(int limit, int count) {
        return limit < 0 || count < limit;
    }
}

