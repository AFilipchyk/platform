package lsfusion.gwt.client.base;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.Messages;

public interface BaseMessages extends Messages {
    String internalServerErrorMessage();

    String actionTimeoutErrorMessage();

    String sessionTimeoutErrorMessage();

    String yes();

    String no();
    
    String ok();
    
    String close();

    String here();

    String showProfile();

    String logoutNotice();

    @Key("locale.ru")
    String localeRu();

    @Key("locale.en")
    String localeEn();

    String loading();

    String more();
    
    String error();

    String logout();

    String username();

    String firstName();

    String lastName();

    String password();

    String repeatPassword();

    String clickToReload();

    String pictureText();

    String login();

    String forgot();

    String register();

    String registration();

    String loginFailed();

    String accessRestricted();

    String cancel();

    String emailPrompt();

    String remind();

    String incorrectEmail();

    String remindError();

    String remindSuccess();

    String loginError();

    String loggedInMessage(String userName);

    String passwordsDontMatch();

    String wrongCaptcha();

    class Instance {
        private static final BaseMessages instance = (BaseMessages) GWT.create(BaseMessages.class);

        public static BaseMessages get() {
            return instance;
        }
    }
}
