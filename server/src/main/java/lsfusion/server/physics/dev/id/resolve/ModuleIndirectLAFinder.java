package lsfusion.server.physics.dev.id.resolve;

import lsfusion.server.language.action.LA;
import lsfusion.server.logics.LogicsModule;

public class ModuleIndirectLAFinder extends ModuleIndirectLAPFinder<LA<?>> {

    @Override
    protected Iterable<LA<?>> getSourceList(LogicsModule module, String name) {
        return module.getNamedActions(name);
    }
}
