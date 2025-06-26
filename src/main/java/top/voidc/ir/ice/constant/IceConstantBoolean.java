package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

import java.util.Objects;

public class IceConstantBoolean extends IceConstantData {
    private final boolean value;

    public IceConstantBoolean(boolean value) {
        super(IceType.I1);
        this.value = value;
    }

    public int getValue() {
        return value ? 1 : 0;
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I1 -> IceConstantData.create(value);
            case I32 -> IceConstantData.create(getValue());
            case F32 -> IceConstantData.create((float) getValue());
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return IceConstantData.create(value);
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " " : "") + getValue();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(getReferenceName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IceConstantBoolean that)) return false;
        return value == that.value;
    }

    @Override
    public IceConstantBoolean and(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).and(other);
        } else if (compare > 0) {
            return this.and(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue() != 0;
            final var otherValue = ((IceConstantBoolean) other).getValue() != 0;
            return IceConstantData.create(thisValue && otherValue);
        }
    }

    @Override
    public IceConstantBoolean or(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).or(other);
        } else if (compare > 0) {
            return this.or(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue() != 0;
            final var otherValue = ((IceConstantBoolean) other).getValue() != 0;
            return IceConstantData.create(thisValue || otherValue);
        }
    }

    @Override
    public IceConstantBoolean not(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).not(other);
        } else if (compare > 0) {
            return this.not(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue() != 0;
            return IceConstantData.create(!thisValue);
        }
    }
}
