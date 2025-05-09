/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.appfunctions;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.appfunctions.AppFunctionException;
import android.app.appfunctions.AppFunctionManager;
import android.app.appfunctions.ExecuteAppFunctionAidlRequest;
import android.app.appfunctions.ExecuteAppFunctionRequest;
import android.app.appfunctions.ExecuteAppFunctionResponse;
import android.app.appfunctions.IAppFunctionEnabledCallback;
import android.app.appfunctions.IAppFunctionManager;
import android.app.appfunctions.IExecuteAppFunctionCallback;
import android.app.appsearch.GenericDocument;
import android.os.Binder;
import android.os.ICancellationSignal;
import android.os.Process;
import android.os.ShellCommand;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Shell command implementation for the {@link AppFunctionManagerService}. */
public class AppFunctionManagerServiceShellCommand extends ShellCommand {

    @NonNull private final IAppFunctionManager mService;

    AppFunctionManagerServiceShellCommand(@NonNull IAppFunctionManager service) {
        mService = Objects.requireNonNull(service);
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("AppFunctionManagerService commands:");
        pw.println("  help");
        pw.println("    Prints this help text.");
        pw.println();
        pw.println(
                "  execute-app-function --package <PACKAGE_NAME> --function <FUNCTION_ID> "
                        + "--parameters <PARAMETERS_JSON> [--user <USER_ID>]");
        pw.println(
                "    Executes an app function for the given package with the provided parameters.");
        pw.println("    --package <PACKAGE_NAME>: The target package name.");
        pw.println("    --function <FUNCTION_ID>: The ID of the app function to execute.");
        pw.println(
                "    --parameters <PARAMETERS_JSON>: JSON string containing the parameters for "
                        + "the function.");
        pw.println(
                "    --user <USER_ID> (optional): The user ID to execute the function under. "
                        + "Defaults to the current user.");
        pw.println();
        pw.println(
                "  set-enabled --package <PACKAGE_NAME> --function <FUNCTION_ID> "
                        + "--state <enable|disable> [--user <USER_ID>]");
        pw.println("    Enables or disables an app function for the specified package.");
        pw.println("    --package <PACKAGE_NAME>: The target package name.");
        pw.println("    --function <FUNCTION_ID>: The ID of the app function.");
        pw.println("    --state <enable|disable|default>: The desired enabled state.");
        pw.println(
                "    --user <USER_ID> (optional): The user ID under which to set the function state"
                        + ". Defaults to the current user.");
        pw.println();
    }

    @Override
    public int onCommand(String cmd) {
        if (cmd == null) {
            return handleDefaultCommands(null);
        }

        try {
            switch (cmd) {
                case "execute-app-function":
                    return runExecuteAppFunction();
                case "set-enabled":
                    return runSetAppFunctionEnabled();
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (Exception e) {
            getOutPrintWriter().println("Exception: " + e);
        }
        return -1;
    }

    private int runSetAppFunctionEnabled() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        String packageName = null;
        String functionId = null;
        int enabledState = -1;
        int userId = ActivityManager.getCurrentUser();
        String opt;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--package":
                    packageName = getNextArgRequired();
                    break;
                case "--function":
                    functionId = getNextArgRequired();
                    break;
                case "--state":
                    enabledState = determineEnabledState(getNextArgRequired());
                    break;
                case "--user":
                    try {
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        pw.println("Invalid user ID: " + getNextArg() + ". Using current user.");
                    }
                    break;
                default:
                    pw.println("Unknown option: " + opt);
                    return -1;
            }
        }

        if (packageName == null) {
            pw.println("Error: --package must be specified.");
            return -1;
        }
        if (functionId == null) {
            pw.println("Error: --function must be specified.");
            return -1;
        }
        if (enabledState == -1) {
            pw.println(
                    "Error: --state must be specified. The accepted values are: "
                            + "`enable`, `disable`, `default`.");
            return -1;
        }

        CountDownLatch countDownLatch = new CountDownLatch(1);

        IAppFunctionEnabledCallback callback =
                new IAppFunctionEnabledCallback.Stub() {
                    @Override
                    public void onSuccess() {
                        pw.println("App function enabled state updated successfully.");
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onError(android.os.ParcelableException exception) {
                        pw.println("Error setting app function state: " + exception);
                        countDownLatch.countDown();
                    }
                };

        mService.setAppFunctionEnabled(
                packageName, functionId, UserHandle.of(userId), enabledState, callback);

        boolean completed = countDownLatch.await(5, TimeUnit.SECONDS);
        if (!completed) {
            pw.println("Timed out");
        }
        pw.flush();

        return 0;
    }

    private int determineEnabledState(String state) {
        switch (state) {
            case "default":
                return AppFunctionManager.APP_FUNCTION_STATE_DEFAULT;
            case "enable":
                return AppFunctionManager.APP_FUNCTION_STATE_ENABLED;
            case "disable":
                return AppFunctionManager.APP_FUNCTION_STATE_DISABLED;
        }

        return -1;
    }

    private int runExecuteAppFunction() throws Exception {
        final PrintWriter pw = getOutPrintWriter();
        String packageName = null;
        String functionId = null;
        String parametersJson = null;
        int userId = ActivityManager.getCurrentUser();
        String opt;

        while ((opt = getNextOption()) != null) {
            switch (opt) {
                case "--package":
                    packageName = getNextArgRequired();
                    break;
                case "--function":
                    functionId = getNextArgRequired();
                    break;
                case "--parameters":
                    parametersJson = getNextArgRequired();
                    break;
                case "--user":
                    try {
                        userId = UserHandle.parseUserArg(getNextArgRequired());
                    } catch (NumberFormatException e) {
                        pw.println("Invalid user ID: " + getNextArg() + ". Using current user.");
                    }
                    break;
                default:
                    pw.println("Unknown option: " + opt);
                    return -1;
            }
        }

        if (packageName == null) {
            pw.println("Error: --package must be specified.");
            return -1;
        }
        if (functionId == null) {
            pw.println("Error: --function must be specified.");
            return -1;
        }
        if (parametersJson == null) {
            pw.println("Error: --parameters must be specified.");
            return -1;
        }

        GenericDocument parameters = parseJsonToGenericDocument(parametersJson);

        ExecuteAppFunctionAidlRequest request =
                new ExecuteAppFunctionAidlRequest(
                        new ExecuteAppFunctionRequest.Builder(packageName, functionId)
                                .setParameters(parameters)
                                .build(),
                        UserHandle.of(userId),
                        getCallingPackage(),
                        SystemClock.elapsedRealtime());

        CountDownLatch countDownLatch = new CountDownLatch(1);

        IExecuteAppFunctionCallback callback =
                new IExecuteAppFunctionCallback.Stub() {

                    @Override
                    public void onSuccess(ExecuteAppFunctionResponse response) {
                        pw.println("App function executed successfully.");
                        pw.println("Function return:");
                        pw.println(response.getResultDocument().toString());
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onError(AppFunctionException e) {
                        Log.d(TAG, "onError: ", e);
                        pw.println("Error executing app function: " + e.getErrorCode() + " - " + e);
                        countDownLatch.countDown();
                    }
                };

        ICancellationSignal cancellationSignal = mService.executeAppFunction(request, callback);

        boolean returned = countDownLatch.await(10, TimeUnit.SECONDS);
        if (!returned) {
            pw.println("Timed out");
            cancellationSignal.cancel();
        }
        pw.flush();

        return 0;
    }

    /**
     * Converts a JSON string to a {@link GenericDocument}.
     *
     * <p>This method parses the provided JSON string and creates a {@link GenericDocument}
     * representation. It extracts the 'id', 'namespace', and 'schemaType' fields from the top-level
     * JSON object to initialize the {@code GenericDocument}. It then iterates through the remaining
     * keys in the JSON object and adds them as properties to the {@code GenericDocument}.
     *
     * <p>Example Input:
     *
     * <pre>{@code
     * {"createNoteParams":{"title":"My title"}}
     * }</pre>
     */
    private static GenericDocument parseJsonToGenericDocument(String jsonString)
            throws JSONException {
        JSONObject json = new JSONObject(jsonString);

        String id = json.optString("id", "");
        String namespace = json.optString("namespace", "");
        String schemaType = json.optString("schemaType", "");

        GenericDocument.Builder builder = new GenericDocument.Builder(id, namespace, schemaType);

        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);

            if (value instanceof String) {
                builder.setPropertyString(key, (String) value);
            } else if (value instanceof Integer || value instanceof Long) {
                builder.setPropertyLong(key, ((Number) value).longValue());
            } else if (value instanceof Double || value instanceof Float) {
                builder.setPropertyDouble(key, ((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                builder.setPropertyBoolean(key, (Boolean) value);
            } else if (value instanceof JSONObject) {
                GenericDocument nestedDocument = parseJsonToGenericDocument(value.toString());
                builder.setPropertyDocument(key, nestedDocument);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                if (array.length() == 0) {
                    continue;
                }

                Object first = array.get(0);
                if (first instanceof String) {
                    String[] arr = new String[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        arr[i] = array.optString(i, null);
                    }
                    builder.setPropertyString(key, arr);
                } else if (first instanceof Integer || first instanceof Long) {
                    long[] arr = new long[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        arr[i] = array.getLong(i);
                    }
                    builder.setPropertyLong(key, arr);
                } else if (first instanceof Double || first instanceof Float) {
                    double[] arr = new double[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        arr[i] = array.getDouble(i);
                    }
                    builder.setPropertyDouble(key, arr);
                } else if (first instanceof Boolean) {
                    boolean[] arr = new boolean[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        arr[i] = array.getBoolean(i);
                    }
                    builder.setPropertyBoolean(key, arr);
                } else if (first instanceof JSONObject) {
                    GenericDocument[] documentArray = new GenericDocument[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        documentArray[i] =
                                parseJsonToGenericDocument(array.getJSONObject(i).toString());
                    }
                    builder.setPropertyDocument(key, documentArray);
                }
            }
        }
        return builder.build();
    }

    private static String getCallingPackage() {
        return switch (Binder.getCallingUid()) {
            case Process.ROOT_UID -> "root";
            case Process.SHELL_UID -> "com.android.shell";
            default -> throw new IllegalAccessError("Only allow shell or root");
        };
    }
}
