package lsfusion.server.language;

import org.antlr.runtime.Parser;

public class ParserInfo {
    private Parser parser;
    // Информация об объявлении метакода (META metaCode), который мы сейчас парсим   
    private String metacodeDefinitionModuleName = null;
    private int metacodeDefinitionLineNumber;
    
    // Информация об использовании метакода (@metaCode), который мы парсим
    private String metacodeCallStr;
    private int lineNumber;

    public ParserInfo(Parser parser, MetaCodeFragment metaCode, String metacodeCallStr, int lineNumber) {
        this.parser = parser;
        if (metaCode != null) {
            this.metacodeDefinitionLineNumber = metaCode.getLineNumber();
            this.metacodeDefinitionModuleName = metaCode.getModuleName();
        }
        this.metacodeCallStr = metacodeCallStr;
        this.lineNumber = lineNumber;
    }

    public Parser getParser() {
        return parser;
    }

    public String getMetacodeCallStr() {
        return metacodeCallStr;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getMetacodeDefinitionModuleName() {
        return metacodeDefinitionModuleName;
    }

    public int getMetacodeDefinitionLineNumber() {
        return metacodeDefinitionLineNumber;
    }
}

