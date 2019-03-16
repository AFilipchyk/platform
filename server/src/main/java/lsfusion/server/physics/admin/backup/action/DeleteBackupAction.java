package lsfusion.server.physics.admin.backup.action;

import com.google.common.base.Throwables;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.DataObject;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.logging.ServerLoggers;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;

import java.io.File;
import java.sql.SQLException;
import java.util.Iterator;

public class DeleteBackupAction extends InternalAction {
    private final ClassPropertyInterface backupInterface;

    public DeleteBackupAction(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        backupInterface = i.next();
    }

    public void executeInternal(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try (ExecutionContext.NewSession<ClassPropertyInterface> newContext = context.newSession()) {
            DataObject backupObject = newContext.getDataKeyValue(backupInterface);

            String backupFilePath = (String) findProperty("file[Backup]").read(newContext, backupObject);
            String backupLogFilePath = (String) findProperty("fileLog[Backup]").read(newContext, backupObject);
            File f = new File(backupFilePath);
            File fLog = new File(backupLogFilePath);
            if (fLog.exists() && !fLog.delete()) {
                fLog.deleteOnExit();
            }
            if (!f.exists() || f.delete()) {
                ServerLoggers.systemLogger.info("Deleted backup " + f.getName());
                findProperty("fileDeleted[Backup]").change(true, newContext, backupObject);
                context.delayUserInteraction(new MessageClientAction("Deleted backup " + f.getName(), "Deleted backup"));
            }
            newContext.apply();

        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }
}