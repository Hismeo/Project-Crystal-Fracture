package org.hismeo.nuquest.core.dialog.context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hismeo.nuquest.api.dialog.IAction;

import static org.hismeo.crystallib.util.JsonUtil.tryGet;
import static org.hismeo.crystallib.util.JsonUtil.tryGetString;

public record DialogActionData(String message, IAction[] action) {
    public static DialogActionData fromJson(JsonElement actionDataElement) {
        if (actionDataElement != null) {
            JsonObject actionDataObject = actionDataElement.getAsJsonObject();
            String message = tryGetString(actionDataObject, "message");

            JsonArray actionsArray = actionDataObject.getAsJsonArray("actions");
            IAction[] actions = new IAction[actionsArray.size()];
            for (int i = 0; i < actionsArray.size(); i++) {
                JsonObject jsonObject = actionsArray.get(i).getAsJsonObject();
                actions[i] = IAction.getAction(tryGetString(jsonObject, "name"));
                JsonElement params = tryGet(jsonObject, "params");
                if (params != null) {
                    actions[i].parseJson(params.getAsJsonObject());
                }
            }
            return new DialogActionData(message, actions);
        }
        return null;
    }
}
