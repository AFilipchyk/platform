package lsfusion.server.logics.property;

import lsfusion.base.lambda.set.FunctionSet;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.physics.dev.i18n.LocalizedString;

public class StoredDataProperty extends DataProperty {

    public StoredDataProperty(LocalizedString caption, ValueClass[] classes, ValueClass value) {
        super(caption, classes, value);

        finalizeInit();
    }

    public boolean isStored() {
        return true;
    }

    // нет
    public static FunctionSet<CalcProperty> set = new FunctionSet<CalcProperty>() {
        public boolean contains(CalcProperty element) {
            return element instanceof StoredDataProperty;
        }
        public boolean isEmpty() {
            return false;
        }
        public boolean isFull() {
            return false;
        }
    };

}
