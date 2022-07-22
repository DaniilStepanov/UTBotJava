package org.utbot.example.objects;

public class AnonymousClassesExample {
    private static final AbstractAnonymousClass staticAnonymousClass = AbstractAnonymousClass.getInstance(1);
    @SuppressWarnings("FieldMayBeFinal")
    private static AbstractAnonymousClass nonFinalAnonymousStatic = AbstractAnonymousClass.getInstance(1);

    public int anonymousClassAsParam(AbstractAnonymousClass abstractAnonymousClass) {
        return abstractAnonymousClass.constValue();
    }

    public int anonymousClassAsStatic() {
        return staticAnonymousClass.constValue();
    }

    public int nonFinalAnonymousStatic() {
        return nonFinalAnonymousStatic.constValue();
    }

    public AbstractAnonymousClass anonymousClassAsResult() {
        int x = 1;
        return AbstractAnonymousClass.getInstance(x);
    }
}
