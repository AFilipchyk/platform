package lsfusion.server.data.table;

import lsfusion.base.col.interfaces.immutable.ImOrderMap;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.base.lambda.set.SFunctionSet;
import lsfusion.base.mutability.TwinImmutableObject;
import lsfusion.base.serialization.BinarySerializable;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.syntax.SQLSyntax;
import lsfusion.server.data.type.Type;
import lsfusion.server.data.type.TypeSerializer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class Field extends TwinImmutableObject implements BinarySerializable {
    protected String name;
    public void setName(String name) {
        this.name = name;
    }
    
    public Type type;
    
    public String getName(SQLSyntax syntax) {
        return syntax.getFieldName(name);
    }
    public String getName() {
        return name;
    }

    private final static Type.Getter<Field> typeGetter = new Type.Getter<Field>() {
        public Type getType(Field key) {
            return key.type;
        }
    };
    public static <F extends Field> Type.Getter<F> typeGetter() {
        return (Type.Getter<F>) typeGetter;
    }

    private final static GetValue<Type, Field> fnTypeGetter = new GetValue<Type, Field>() {
        public Type getMapValue(Field value) {
            return value.type;
        }
    };
    public static <F extends Field> GetValue<Type, F> fnTypeGetter() {
        return (GetValue<Type, F>) fnTypeGetter;
    }

    public static <F extends Field> GetValue<String, F> nameGetter(final SQLSyntax syntax) {
        return (GetValue<String, F>) new GetValue<String, Field>() {
            public String getMapValue(Field value) {
                return value.getName(syntax);
            }};
    }

    public static <F extends Field> GetValue<String, F> nameGetter() {
        return (GetValue<String, F>) new GetValue<String, Field>() {
            public String getMapValue(Field value) {
                return value.getName();
            }};
    }

    public final static SFunctionSet<Field> onlyKeys = new SFunctionSet<Field>() {
        public boolean contains(Field element) {
            return element instanceof KeyField;
        }
    };

    public final static SFunctionSet<Field> onlyProps = new SFunctionSet<Field>() {
        public boolean contains(Field element) {
            return element instanceof PropertyField;
        }
    };

    protected Field(String name,Type type) {
        this.name = name;
        this.type = type;
        assert type != null;
    }

    public static String getDeclare(ImOrderMap<String, Type> map, final SQLSyntax syntax, final TypeEnvironment typeEnv) {
        return map.mapOrderValues(new GetValue<String, Type>() {
            public String getMapValue(Type value) {
                return value.getDB(syntax, typeEnv);
            }}).toString(" ", ",");
    }
    
    public String getDeclare(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return getName(syntax) + " " + type.getDB(syntax, typeEnv);
    }

    public String toString() {
        return name;
    }

    public void serialize(DataOutputStream outStream) throws IOException {
        outStream.writeByte(getType());
        outStream.writeUTF(name);
        TypeSerializer.serializeType(outStream,type);
    }

    protected Field(DataInputStream inStream) throws IOException {
        name = inStream.readUTF();
        type = TypeSerializer.deserializeType(inStream);
    }

    public static Field deserialize(DataInputStream inStream) throws IOException {
        int type = inStream.readByte();
        if(type==0) return new KeyField(inStream);
        if(type==1) return new PropertyField(inStream);

        throw new IOException();
    }

    abstract byte getType();

    public boolean calcTwins(TwinImmutableObject o) {
        return name.equals(((Field)o).name) && type.equals(((Field)o).type);
    }

    public int immutableHashCode() {
        return (getClass().hashCode() * 31 + name.hashCode()) * 31 + type.hashCode();
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(name);
        type.write(out);
    }
}
