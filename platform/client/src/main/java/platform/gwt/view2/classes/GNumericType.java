package platform.gwt.view2.classes;

public class GNumericType extends GDoubleType {
    private int length = 10;
    private int precision = 2;

    public GNumericType() {}

    public GNumericType(int length, int precision) {
        this.length = length;
        this.precision = precision;
    }
}
