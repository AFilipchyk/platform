package lsfusion.interop.form.order;

public enum Scroll {
    HOME,END;

    public static Scroll deserialize(byte data) {
        switch(data) {
            case 0:
                return HOME;
            case 1:
                return END;
        }
        throw new RuntimeException("Deserialize Scroll");
    }

    public byte serialize() {
        switch(this) {
            case HOME:
                return 0;
            case END:
                return 1;
        }
        throw new RuntimeException("Serialize Scroll");
    }

}
