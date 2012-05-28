CodeMirror.defineMode("fusion", function(config, parserConfig) {
    function words(str) {
        var obj = {}, words = str.split(" ");
        for (var i = 0; i < words.length; ++i) {
            obj[words[i]] = true;
        }
        return obj;
    }

    var types = words("INTEGER DOUBLE LONG BOOLEAN DATE DATETIME TEXT STRING ISTRING TIME");
    var keywords = words("ABSTRACT ACTION ADD ADDFORM ADDOBJ ADDSESSIONFORM AFTER " + 
                           "AGGPROP ALL AND APPLY AS ASC ASCHANGE ASSIGNED ATTACH " + 
                           "AUTOAPPLY AUTOSET BACKGROUND BCC BEFORE BOTTOM BREAK BY " + 
                           "CAPTION CASCADE CASE CC CENTER CHANGECLASS CHANGED CHECK " + 
                           "CHECKED CLASS COLOR COLUMNS CONCAT CONFIRM CONSTRAINT " + 
                           "CONTAINER CUSTOM CUSTOMFILE CYCLES DATA DEFAULT DESC DESIGN " + 
                           "DIALOG DO DOCKING DOCX DRAWROOT ECHO EDIT EDITABLE EDITFORM " + 
                           "EDITKEY EDITSESSIONFORM ELSE EMAIL END EQUAL EXCELFILE " + 
                           "EXCEPTLAST EXCLUSIVE EXEC EXTEND FALSE FILTER FILTERGROUP " + 
                           "FILTERS FIXED FIXEDCHARWIDTH FOOTER FOR FORCE FOREGROUND " + 
                           "FORM FORMULA FROM FULLSCREEN GRID GROUP HALIGN HEADER " + 
                           "HIDE HIDESCROLLBARS HIDETITLE HINTNOUPDATE HINTTABLE HORIZONTAL " + 
                           "HTML IF IMAGE IMAGEFILE IMPORT IMPOSSIBLE IN INCREMENT " + 
                           "INDEX INDEXED INIT INLINE INPUT INTERSECT IS LEFT LENGTH " + 
                           "LIMIT LIST LOADFILE LOCAL LOGGABLE MAX MAXCHARWIDTH MESSAGE " + 
                           "META MIN MINCHARWIDTH MODAL MODULE NAME NAVIGATOR NEW " + 
                           "NEWSESSION NO NOT NOTHING NULL NUMERIC OBJECT OBJECTS " + 
                           "OBJVALUE ONCHANGE OPENFILE OR ORDER OVERRIDE PAGESIZE " + 
                           "PANEL PARENT PARTITION PDF PDFFILE PERSISTENT POSITION " + 
                           "PREFCHARWIDTH PREV PRINT PROPERTIES PROPERTY PROPORTION " + 
                           "READONLY READONLYIF RECURSION REGEXP REMOVE REPORTFILE " + 
                           "REQUEST RESOLVE RETURN RGB RIGHT RIGHTBOTTOM ROUND RTF " + 
                           "SELECTION SELECTOR SESSION SET SHORTCUT SHOW SHOWIF SPLITH " + 
                           "SPLITV STATIC STEP STRICT SUBJECT SUM TABBED TABLE TEXTHALIGN " + 
                           "TEXTVALIGN THE THEN TO TODRAW TOOLBAR TOP TREE TRUE UNGROUP " + 
                           "UNION UNIQUE UPDATE VALIGN VERTICAL WHEN WHERE WHILE WINDOW " + 
                           "WORDFILE XOR");

    var isOperatorChar = /[+\-*&%=<>!?|@#]/;

    return {
        token: function(stream, state) {
            if (stream.eatSpace()) {
                return null;
            }

            var ch = stream.next();
            if (ch == "'") {
                var escaped = false, next;
                while ((next = stream.next()) != null) {
                    if (next == ch && !escaped) {
                        return "string";
                    }
                    escaped = !escaped && next == "\\";
                }
                return "string";
            } else if (/[\[\]{}\(\),;\:\.]/.test(ch)) {
                return "bracket";
            } else if (/\d/.test(ch)) {
                stream.match(/^\d*(\.\d+)?/);
                return "number";
            } else if (ch == "/") {
                if (stream.eat("/")) {
                    stream.skipToEnd();
                    return "comment";
                }
                return "operator";
            } else if (ch == "$") {
                stream.eatWhile(/[\d]/);
                return "attribute";
            } else if (isOperatorChar.test(ch)) {
                stream.eatWhile(isOperatorChar);
                return "operator";
            } else {
                stream.eatWhile(/[\w]/);
                var word = stream.current();
                var istype = types.propertyIsEnumerable(word) && types[word];

                if (istype) {
                    return "type";
                }
                var iskeyword = keywords.propertyIsEnumerable(word) && keywords[word];
                return iskeyword ? "keyword" : "variable";
            }
        }
    };
});
