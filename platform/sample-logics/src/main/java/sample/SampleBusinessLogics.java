package sample;

import net.sf.jasperreports.engine.JRException;
import platform.server.auth.User;
import platform.server.data.sql.DataAdapter;
import platform.server.logics.BusinessLogics;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

public class SampleBusinessLogics extends BusinessLogics<SampleBusinessLogics> {
    private SampleLogicsModule sampleLM;

    public SampleBusinessLogics(DataAdapter iAdapter,int port) throws IOException, ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException, JRException, FileNotFoundException {
        super(iAdapter,port);
    }

    @Override
    protected void createModules() throws IOException {
        super.createModules();
        sampleLM = new SampleLogicsModule(LM);
        sampleLM.setRequiredModules(Arrays.asList("BaseLogicsModule"));
        addLogicsModule(sampleLM);
    }

    protected void initAuthentication() throws ClassNotFoundException, SQLException, IllegalAccessException, InstantiationException {
        User admin = addUser("admin", "fusion");
    }


    @Override
    public BusinessLogics getBL() {
        return this;
    }
}
