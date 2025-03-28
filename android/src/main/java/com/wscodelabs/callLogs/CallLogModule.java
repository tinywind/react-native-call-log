package com.wscodelabs.callLogs;

import android.os.Build;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.database.Cursor;
import android.content.Context;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import org.json.JSONArray;
import org.json.JSONException;

import javax.annotation.Nullable;

public class CallLogModule extends ReactContextBaseJavaModule {

    public static final String SUBSCRIPTION_ID = "subscription_id";

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24 이상 Nougat
                projection = new String[]{
                        Calls.NUMBER,
                        Calls.TYPE,
                        Calls.DATE,
                        Calls.DURATION,
                        Calls.CACHED_NAME,
                        "subscription_id"  // ✅ 실제 문자열 사용 (필수!)
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
                    CallLog.Calls.DATE + " DESC"
            );

            WritableArray result = Arguments.createArray();

            if (cursor == null) {
                promise.resolve(result);
                return;
            }

            int callLogCount = 0;

            final int NUMBER_COLUMN_INDEX = cursor.getColumnIndex(Calls.NUMBER);
            final int TYPE_COLUMN_INDEX = cursor.getColumnIndex(Calls.TYPE);
            final int DATE_COLUMN_INDEX = cursor.getColumnIndex(Calls.DATE);
            final int DURATION_COLUMN_INDEX = cursor.getColumnIndex(Calls.DURATION);
            final int NAME_COLUMN_INDEX = cursor.getColumnIndex(Calls.CACHED_NAME);

            final int SUBSCRIPTION_ID_INDEX = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? cursor.getColumnIndex("subscription_id")
                    : -1;

            while (cursor.moveToNext() && this.shouldContinue(limit, callLogCount)) {
                String phoneNumber = cursor.getString(NUMBER_COLUMN_INDEX);
                int duration = cursor.getInt(DURATION_COLUMN_INDEX);
                String name = cursor.getString(NAME_COLUMN_INDEX);
                String timestampStr = cursor.getString(DATE_COLUMN_INDEX);
                String type = this.resolveCallType(cursor.getInt(TYPE_COLUMN_INDEX));

                WritableMap callLog = Arguments.createMap();
                callLog.putString("phoneNumber", phoneNumber);
                callLog.putInt("duration", duration);
                callLog.putString("name", name);
                callLog.putString("timestamp", timestampStr);
                callLog.putString("dateTime", SimpleDateFormat.getDateTimeInstance().format(new Date(Long.parseLong(timestampStr))));
                callLog.putString("type", type);
                callLog.putInt("rawType", cursor.getInt(TYPE_COLUMN_INDEX));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && SUBSCRIPTION_ID_INDEX != -1) {
                    callLog.putInt("subscriptionId", cursor.getInt(SUBSCRIPTION_ID_INDEX));
                } else {
                    callLog.putInt("subscriptionId", -1);
                }

                result.pushMap(callLog);
                callLogCount++;
            }

            cursor.close();
            promise.resolve(result);
        } catch (Exception e) {
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

