package lsfusion.server.mail;

import jasperapi.ReportGenerator;
import jasperapi.ReportHTMLExporter;
import lsfusion.base.BaseUtils;
import lsfusion.base.ByteArray;
import lsfusion.base.Pair;
import lsfusion.base.col.MapFact;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.interop.form.ReportGenerationData;
import lsfusion.server.ServerLoggers;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.EmailLogicsModule;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.CalcPropertyInterfaceImplement;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.PropertyInterface;
import lsfusion.server.logics.property.actions.SystemExplicitActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.remote.InteractiveFormReportManager;
import net.sf.jasperreports.engine.JRAbstractExporter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.JRRtfExporter;
import net.sf.jasperreports.engine.export.JRXlsAbstractExporterParameter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import org.apache.log4j.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

import static javax.mail.Message.RecipientType.TO;
import static lsfusion.base.BaseUtils.nullTrim;
import static lsfusion.base.BaseUtils.rtrim;
import static lsfusion.server.context.ThreadLocalContext.localize;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public class SendEmailActionProperty extends SystemExplicitActionProperty {
    private final static Logger logger = ServerLoggers.mailLogger;

    public enum FormStorageType {INLINE, ATTACH}

    private CalcPropertyInterfaceImplement<ClassPropertyInterface> fromAddressAccount;
    private CalcPropertyInterfaceImplement<ClassPropertyInterface> subject;

    private List<CalcPropertyInterfaceImplement<ClassPropertyInterface>> recipients = new ArrayList<>();
    private List<Message.RecipientType> recipientTypes = new ArrayList<>();

    private final List<FormEntity> forms = new ArrayList<>();
    private final List<AttachmentFormat> formats = new ArrayList<>();
    private final List<FormStorageType> storageTypes = new ArrayList<>();
    private final List<Map<ObjectEntity, CalcPropertyInterfaceImplement<ClassPropertyInterface>>> mapObjects = new ArrayList<>();
    private final List<CalcPropertyInterfaceImplement> attachmentProps = new ArrayList<>();
    private final List<CalcPropertyInterfaceImplement> attachFileNames = new ArrayList<>();
    private final List<CalcPropertyInterfaceImplement> attachFiles = new ArrayList<>();
    private final List<CalcPropertyInterfaceImplement> inlineTexts = new ArrayList<>();

    public SendEmailActionProperty(LocalizedString caption, ValueClass[] classes) {
        super(caption, classes);

        drawOptions.setAskConfirm(true);
        drawOptions.setImage("email.png");
    }

    @Override
    protected boolean isVolatile() { // формы используются, не определишь getUsedProps
        return true;
    }

    public void setFromAddressAccount(CalcPropertyInterfaceImplement<ClassPropertyInterface> fromAddressAccount) {
        this.fromAddressAccount = fromAddressAccount;
    }

    public void setSubject(CalcPropertyInterfaceImplement<ClassPropertyInterface> subject) {
        this.subject = subject;
    }

    public <R extends PropertyInterface> void addRecipient(CalcPropertyInterfaceImplement<ClassPropertyInterface> recipient, Message.RecipientType type) {
        recipients.add(recipient);
        recipientTypes.add(type);
    }

    public void addInlineForm(FormEntity form, Map<ObjectEntity, CalcPropertyInterfaceImplement<ClassPropertyInterface>> objects) {
        forms.add(form);
        formats.add(AttachmentFormat.HTML);
        storageTypes.add(FormStorageType.INLINE);
        mapObjects.add(objects);
        attachmentProps.add(null);
    }

    public void addAttachmentForm(FormEntity form, AttachmentFormat format, Map<ObjectEntity, CalcPropertyInterfaceImplement<ClassPropertyInterface>> objects, CalcPropertyInterfaceImplement attachmentNameProp) {
        forms.add(form);
        formats.add(format);
        storageTypes.add(FormStorageType.ATTACH);
        mapObjects.add(objects);
        attachmentProps.add(attachmentNameProp);
    }
    
    public void addAttachmentFile(CalcPropertyInterfaceImplement fileName, CalcPropertyInterfaceImplement file) {
        attachFileNames.add(fileName);
        attachFiles.add(file);
    }

    public void addInlineText(CalcPropertyInterfaceImplement inlineText) {
        inlineTexts.add(inlineText);
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        EmailLogicsModule emailLM = context.getBL().emailLM;
        try {
            assert subject != null && fromAddressAccount != null;

            List<EmailSender.AttachmentProperties> attachments = new ArrayList<>();
            List<String> inlineForms = new ArrayList<>();
            Map<ByteArray, String> attachmentFiles = new HashMap<>();

            assert forms.size() == storageTypes.size() && forms.size() == formats.size() && forms.size() == attachmentProps.size() && forms.size() == mapObjects.size();

            for (int i = 0; i < forms.size(); i++) {
                FormEntity form = forms.get(i);
                FormStorageType storageType = storageTypes.get(i);
                AttachmentFormat attachmentFormat = formats.get(i);
                CalcPropertyInterfaceImplement attachmentProp = attachmentProps.get(i);
                Map<ObjectEntity, CalcPropertyInterfaceImplement<ClassPropertyInterface>> objectsImplements = mapObjects.get(i);

                FormInstance remoteForm = createReportForm(context, form, objectsImplements);

                // если объекты подошли
                if (remoteForm != null) {
                    String filePath = createReportFile(remoteForm, storageType == FormStorageType.INLINE, attachmentFormat, attachmentFiles);
                    if (storageType == FormStorageType.INLINE) {
                        inlineForms.add(filePath);
                    } else {
                        EmailSender.AttachmentProperties attachment = createAttachment(form, attachmentFormat, attachmentProp, context, filePath);
                        attachments.add(attachment);
                    }
                }
            }

            Map<String, Message.RecipientType> recipients = getRecipientEmails(context);

            String fromAddress = (String) fromAddressAccount.read(context, context.getKeys());
            ObjectValue account = fromAddress != null ?
                    emailLM.inboxAccount.readClasses(context, new DataObject(fromAddress)) :
                    emailLM.defaultInboxAccount.readClasses(context);

            Map<ByteArray, Pair<String, String>> customAttachments = createCustomAttachments(context);

            List<String> texts = new ArrayList<>();
            for(CalcPropertyInterfaceImplement inlineText : inlineTexts) {
                texts.add((String) inlineText.read(context, context.getKeys()));
            }

            if (account instanceof DataObject) {
                String encryptedConnectionType = (String) emailLM.nameEncryptedConnectionTypeAccount.read(context, account);
                String smtpHostAccount = (String) emailLM.smtpHostAccount.read(context, account);
                String smtpPortAccount = (String) emailLM.smtpPortAccount.read(context, account);

                String fromAddressAccount = (String) (fromAddress != null ? fromAddress : emailLM.fromAddressAccount.read(context, account));

                String subject = (String) this.subject.read(context, context.getKeys());
                String nameAccount = (String) emailLM.nameAccount.read(context, account);
                String passwordAccount = (String) emailLM.passwordAccount.read(context, account);
                
                if (emailLM.disableAccount.read(context, account) != null) {
                    logger.error(localize("{mail.disabled}"));
                    return;
                }

                sendEmail(context, smtpHostAccount, smtpPortAccount, nameAccount, passwordAccount, encryptedConnectionType, fromAddressAccount, subject, recipients, inlineForms, attachments, attachmentFiles, customAttachments, texts);
            }
        } catch (Throwable e) {
            String errorMessage = localize("{mail.failed.to.send.mail}") + " : " + e.toString();
            logger.error(errorMessage);
            context.delayUserInterfaction(new MessageClientAction(errorMessage, localize("{mail.sending}")));

            logError(context, localize("{mail.failed.to.send.mail}") + " : " + e.toString());
            e.printStackTrace();
        }
    }

    private void sendEmail(ExecutionContext context, String smtpHostAccount, String smtpPortAccount, String userName, String password, String encryptedConnectionType, String fromAddressAccount, String subject, Map<String, Message.RecipientType> recipientEmails, List<String> inlineForms, List<EmailSender.AttachmentProperties> attachments, Map<ByteArray, String> attachmentFiles, Map<ByteArray, Pair<String, String>> customAttachments, List<String> inlineTexts) throws MessagingException, IOException, ScriptingErrorLog.SemanticErrorException {
        if (smtpHostAccount == null || fromAddressAccount == null) {
            logError(context, localize("{mail.smtp.host.or.sender.not.specified.letters.will.not.be.sent}"));
            return;
        }

        if (recipientEmails.isEmpty()) {
            logError(context, localize("{mail.recipient.not.specified}"));
            return;
        }

        EmailSender sender = new EmailSender(
                nullTrim(smtpHostAccount),
                nullTrim(smtpPortAccount),
                nullTrim(encryptedConnectionType),
                nullTrim(fromAddressAccount),
                nullTrim(userName),
                nullTrim(password),
                recipientEmails
        );

        sender.sendMail(context, subject, inlineForms, attachments, attachmentFiles, customAttachments, inlineTexts);
    }

    private Map<String, Message.RecipientType> getRecipientEmails(ExecutionContext context) throws SQLException, SQLHandledException {
        assert recipients.size() == recipientTypes.size();

        Pattern p = Pattern.compile("^([A-Za-z0-9_-]+\\.)*[A-Za-z0-9_-]+@[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*\\.[A-Za-z]{2,10}$");

        Map<String, Message.RecipientType> recipientEmails = new HashMap<>();
        for (int i = 0; i < recipients.size(); ++i) {
            CalcPropertyInterfaceImplement<ClassPropertyInterface> recipient = recipients.get(i);
            Message.RecipientType recipientType = recipientTypes.get(i);

            String recipientEmailList = (String) recipient.read(context, context.getKeys());
            if (recipientEmailList != null) {
                String[] emails = recipientEmailList.replace(',',';').replace(":", ";").split(";");
                for (String email : emails) {
                    email = trimToNull(email);
                    if (email == null || !p.matcher(email).matches()) {
                        if(email != null)
                            context.requestUserInteraction(new MessageClientAction("Invalid email: " + email, "Invalid email"));
                        continue;
                    }

                    // приоритет отдается TO, так как без него письмо не улетит
                    if (TO.equals(recipientType) || !recipientEmails.containsKey(email)) {
                        recipientEmails.put(email, recipientType);
                    }
                }
            }
        }
        return recipientEmails;
    }
    
    private Map<ByteArray, Pair<String, String>> createCustomAttachments(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        Map<ByteArray, Pair<String, String>> result = new LinkedHashMap<>();
        for (int i = 0; i < attachFileNames.size(); i++) {
            String name;
            CalcPropertyInterfaceImplement attachFileNameProp = attachFileNames.get(i);
            if (attachFileNameProp != null) {
                 name = (String) attachFileNameProp.read(context, context.getKeys());
            } else {
                 name = "attachment" + i;
            }
            
            byte[] file = (byte[]) attachFiles.get(i).read(context, context.getKeys());
            if (file != null) {
                String extension = BaseUtils.getExtension(file);
                result.put(new ByteArray(BaseUtils.getFile(file)), new Pair<>(name + "." + extension, extension));
            }
        }
        return result;
    }

    private EmailSender.AttachmentProperties createAttachment(FormEntity form, AttachmentFormat attachmentFormat, CalcPropertyInterfaceImplement attachmentNameProp, ExecutionContext context, String filePath) throws SQLException, SQLHandledException {
        assert attachmentFormat != null;

        String attachmentName = null;
        if (attachmentNameProp != null) {
            attachmentName = (String) attachmentNameProp.read(context, context.getKeys());
        }
        if (attachmentName == null) {
            attachmentName = localize(form.getCaption());
        }
        attachmentName = rtrim(attachmentName.replace('"', '\''));

        // добавляем расширение, поскольку видимо не все почтовые клиенты правильно его определяют по mimeType
        attachmentName += attachmentFormat.getExtension();

        return new EmailSender.AttachmentProperties(filePath, attachmentName, attachmentFormat);
    }

    private FormInstance createReportForm(ExecutionContext context, FormEntity form, Map<ObjectEntity, CalcPropertyInterfaceImplement<ClassPropertyInterface>> objectsImplements) throws SQLException, SQLHandledException {
        Map<ObjectEntity, ObjectValue> objectValues = new HashMap<>();
        for (Map.Entry<ObjectEntity, CalcPropertyInterfaceImplement<ClassPropertyInterface>> objectImpl : objectsImplements.entrySet())
            objectValues.put(objectImpl.getKey(), objectImpl.getValue().readClasses(context, context.getKeys()));

        return context.createFormInstance(form, MapFact.fromJavaMap(objectValues));
    }

    private String createReportFile(FormInstance remoteForm, boolean inlineForm, AttachmentFormat attachmentFormat, Map<ByteArray, String> attachmentFiles) throws ClassNotFoundException, IOException, JRException {

        boolean toExcel = attachmentFormat != null && attachmentFormat.equals(AttachmentFormat.XLSX);
        ReportGenerationData generationData = new InteractiveFormReportManager(remoteForm).getReportData(toExcel);

        ReportGenerator report = new ReportGenerator(generationData);
        JasperPrint print = report.createReport(inlineForm || toExcel, attachmentFiles);
        print.setProperty(JRXlsAbstractExporterParameter.PROPERTY_DETECT_CELL_TYPE, "true");
        try {
            String filePath = File.createTempFile("lsfReport", attachmentFormat != null ? attachmentFormat.getExtension() : null).getAbsolutePath();
            JRAbstractExporter exporter = createExporter(attachmentFormat);

            exporter.setParameter(JRExporterParameter.OUTPUT_FILE_NAME, filePath);
            exporter.setParameter(JRExporterParameter.JASPER_PRINT, print);
            exporter.exportReport();

            return filePath;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JRAbstractExporter createExporter(AttachmentFormat format) {
        JRAbstractExporter exporter;
        switch (format) {
            case PDF:
                exporter = new JRPdfExporter();
                break;
            case DOCX:
                exporter = new JRDocxExporter();
                break;
            case RTF:
                exporter = new JRRtfExporter();
                break;
            case XLSX:
                exporter = new JRXlsxExporter();
                break;
            default:
                exporter = new ReportHTMLExporter();
                // этот параметр вырезан. см. ReportHTMLExporter
//                exporter.setParameter(JRHtmlExporterParameter.IS_USING_IMAGES_TO_ALIGN, false);
                break;
        }
        return exporter;
    }

    private void logError(ExecutionContext context, String errorMessage) {
        logger.error(errorMessage);
        context.delayUserInterfaction(new MessageClientAction(errorMessage, localize("{mail.sending}")));
    }
}
