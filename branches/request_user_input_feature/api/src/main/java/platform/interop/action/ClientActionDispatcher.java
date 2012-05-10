package platform.interop.action;

// такая дебильная схема с Dispatcher'ом чтобы модульность не нарушать
public interface ClientActionDispatcher {

    public void execute(DenyCloseFormClientAction action);

    public void execute(FormClientAction action);

    public void execute(DialogClientAction action);

    public Object execute(RuntimeClientAction action);

    public void execute(ExportFileClientAction action);

    public Object execute(ImportFileClientAction action);

    public void execute(SleepClientAction action);

    public Object execute(MessageFileClientAction action);

    public void execute(UserChangedClientAction action);
    
    public void execute(UserReloginClientAction action);

    public void execute(MessageClientAction action);

    public int execute(ConfirmClientAction action);

    public void execute(LogMessageClientAction action);

    public void execute(ApplyClientAction action);

    public void execute(OpenFileClientAction action);

    public void execute(AudioClientAction action);

    public void execute(ProcessFormChangesClientAction action);

    public void execute(UpdateCurrentClassClientAction action);

    public Object execute(RequestUserInputClientAction action);
}
