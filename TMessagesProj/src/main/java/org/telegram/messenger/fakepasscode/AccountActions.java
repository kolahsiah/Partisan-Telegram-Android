package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AccountActions {
    public int accountNum;
    private final FakePasscode fakePasscode;

    private <T extends AccountAction> T getAction(List<T> actions) {
        return actions.stream().filter(a -> a.accountNum == accountNum).findFirst().orElse(null);
    }

    private <T extends AccountAction> T getOrCreateAction(List<T> actions, Class<T> clazz) {
        return actions.stream().filter(a -> a.accountNum == accountNum)
                .findFirst().orElseGet(() -> {
                    try {
                        T action = clazz.newInstance();
                        action.accountNum = accountNum;
                        actions.add(action);
                        return action;
                    } catch (Exception ignored) {
                        return null;
                    }
                });
    }

    private <T extends AccountAction> void toggleAction(List<T> actions, Class<T> clazz) {
        T action = getAction(actions);
        if (action != null) {
            actions.removeIf(a -> a.accountNum == accountNum);
        } else {
            try {
                action = clazz.newInstance();
                action.accountNum = accountNum;
                actions.add(action);
            } catch (Exception ignored) {
            }
        }
        SharedConfig.saveConfig();
    }

    public RemoveChatsAction getRemoveChatsAction() { return getOrCreateAction(fakePasscode.removeChatsActions, RemoveChatsAction.class); }
    public TelegramMessageAction getMessageAction() { return getOrCreateAction(fakePasscode.telegramMessageAction, TelegramMessageAction.class); }

    public ArrayList<Integer> getChatsToRemove() {
        return getRemoveChatsAction().chatsToRemove;
    }

    public void setChatsToRemove(ArrayList<Integer> chats) {
        getRemoveChatsAction().chatsToRemove = chats;
        SharedConfig.saveConfig();
    }

    public void toggleDeleteContactsAction() { toggleAction(fakePasscode.deleteContactsActions, DeleteContactsAction.class); }
    public void toggleDeleteStickersAction() { toggleAction(fakePasscode.deleteStickersActions, DeleteStickersAction.class); }
    public void toggleClearSearchHistoryAction() { toggleAction(fakePasscode.clearSearchHistoryActions, ClearSearchHistoryAction.class); }
    public void toggleTerminateOtherSessionsAction() { toggleAction(fakePasscode.terminateOtherSessionsActions, TerminateOtherSessionsAction.class); }
    public void toggleLogOutAction() { toggleAction(fakePasscode.logOutActions, LogOutAction.class); }

    public boolean isDeleteContacts() { return getAction(fakePasscode.deleteContactsActions) != null; }
    public boolean isDeleteStickers() { return getAction(fakePasscode.deleteStickersActions) != null; }
    public boolean isClearSearchHistory() { return getAction(fakePasscode.clearSearchHistoryActions) != null; }
    public boolean isTerminateOtherSessions() { return getAction(fakePasscode.terminateOtherSessionsActions) != null; }
    public boolean isLogOut() { return getAction(fakePasscode.logOutActions) != null; }

    public AccountActions(int accountNum, FakePasscode fakePasscode)
    {
        this.accountNum = accountNum;
        this.fakePasscode = fakePasscode;
    }

    public String getPhone() {
        return fakePasscode.phoneNumbers.getOrDefault(accountNum, "");
    }

    public void setPhone(String phone) {
        fakePasscode.phoneNumbers.put(accountNum, phone);
    }
}
