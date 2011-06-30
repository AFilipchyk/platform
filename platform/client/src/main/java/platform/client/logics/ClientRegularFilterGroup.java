package platform.client.logics;

import platform.base.OrderedMap;
import platform.base.context.ApplicationContext;
import platform.client.ClientResourceBundle;
import platform.client.serialization.ClientSerializationPool;
import platform.gwt.view.GRegularFilterGroup;
import platform.interop.form.layout.SimplexConstraints;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientRegularFilterGroup extends ClientComponent {

    public List<ClientRegularFilter> filters = new ArrayList<ClientRegularFilter>();

    public OrderedMap<ClientPropertyDraw, Boolean> nullOrders = new OrderedMap<ClientPropertyDraw, Boolean>();

    public int defaultFilter = -1;

    public ClientRegularFilterGroup() {

    }

    public ClientRegularFilterGroup(int ID, ApplicationContext context) {
        super(ID, context);
    }

    @Override
    public SimplexConstraints<ClientComponent> getDefaultConstraints() {
        return SimplexConstraints.getRegularFilterGroupDefaultConstraints(super.getDefaultConstraints());
    }

    @Override
    public void customSerialize(ClientSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        super.customSerialize(pool, outStream, serializationType);

        pool.serializeCollection(outStream, filters);

        outStream.writeInt(nullOrders.size());
        for (Map.Entry<ClientPropertyDraw, Boolean> entry : nullOrders.entrySet()) {
            pool.serializeObject(outStream, entry.getKey(), serializationType);
            outStream.writeBoolean(entry.getValue());
        }
    }

    @Override
    public void customDeserialize(ClientSerializationPool pool, DataInputStream inStream) throws IOException {
        super.customDeserialize(pool, inStream);

        filters = pool.deserializeList(inStream);

        defaultFilter = inStream.readInt();

        int orderCount = inStream.readInt();
        for (int i = 0; i < orderCount; i++) {
            ClientPropertyDraw order = pool.deserializeObject(inStream);
            nullOrders.put(order, inStream.readBoolean());
        }
    }

    @Override
    public String getCaption() {
        return ClientResourceBundle.getString("descriptor.filter");
    }

    @Override
    public String toString() {
        return filters.toString();
    }

    public String getCodeClass() {
        return "RegularFilterGroupView";
    }

    @Override
    public String getCodeConstructor() {
        return "design.createRegularFilterGroup(" + getID() + ")";
    }

    public String getCodeConstructor(String descriptorName) {
        return "design.createRegularFilterGroup(" + descriptorName + ")";
    }

    @Override
    public GRegularFilterGroup getGwtComponent() {
        if (gwtFilterGroup == null) {
            gwtFilterGroup = new GRegularFilterGroup();

            initGwtComponent(gwtFilterGroup);
            gwtFilterGroup.defaultFilter = defaultFilter;

            for (ClientRegularFilter filter : filters) {
                gwtFilterGroup.filters.add(filter.getGwtRegularFilter());
            }
        }
        return gwtFilterGroup;
    }

    private GRegularFilterGroup gwtFilterGroup;
    public GRegularFilterGroup getGwtRegularFilterGroup() {
        return getGwtComponent();
    }
}
