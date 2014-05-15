package com.google.android.vending.expansion.downloader;

import android.app.Activity;
import android.content.Context;

/**
 * Created with IntelliJ IDEA.
 * User: svenb
 * Date: 10.03.14
 * Time: 10:18
 * To change this template use File | Settings | File Templates.
 */
public class FakeR {
    private Context context;
    private String packageName;

    public FakeR(Activity activity) {
        context = activity.getApplicationContext();
        packageName = context.getPackageName();
    }

    public FakeR(Context context) {
        this.context = context;
        packageName = context.getPackageName();
    }

    public int getId(String group, String key) {
        return context.getResources().getIdentifier(key, group, packageName);
    }

    public int getId(Context context, String group, String key) {
        return context.getResources().getIdentifier(key, group, context.getPackageName());
    }
}
