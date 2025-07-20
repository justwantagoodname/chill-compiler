package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

import java.util.Objects;

public class IceConstantFloat extends IceConstantData {
    private final float value;

    public IceConstantFloat(float value) {
        super(IceType.F32);
        this.value = value;
    }

    public float getValue() {
        return value;
    }

    @Override
    public String getReferenceName(boolean withType) {
        // 一定要先转float再转回double确保没有超出的精度
        final var bits = Double.doubleToRawLongBits(value);
        return (withType ? getType() + " " : "") + "0x" + Long.toHexString(bits).toUpperCase();
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I32 -> IceConstantData.create((int) value);
            case I1 -> IceConstantData.create(getValue() != 0);
            case F32 -> this.clone();
            case F64 -> IceConstantData.create((double) value);
            default -> throw new IllegalStateException("Unexpected value: " + targetType);
        };
    }

    @Override
    public IceConstantData clone() {
        return IceConstantData.create(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IceConstantFloat that)) return false;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public IceConstantData plus(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).plus(other);
        } else if (compare > 0) {
            return this.plus(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue + otherValue);
        }
    }

    @Override
    public IceConstantData minus(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).minus(other);
        } else if (compare > 0) {
            return this.minus(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue - otherValue);
        }
    }

    @Override
    public IceConstantData multiply(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).multiply(other);
        } else if (compare > 0) {
            return this.multiply(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue * otherValue);
        }
    }

    @Override
    public IceConstantData divide(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).divide(other);
        } else if (compare > 0) {
            return this.divide(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            if (otherValue == 0.0f) {
                throw new ArithmeticException("Division by zero");
            }
            return IceConstantData.create(thisValue / otherValue);
        }
    }

    @Override
    public IceConstantBoolean lt(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).lt(other);
        } else if (compare > 0) {
            return this.lt(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue < otherValue);
        }
    }

    @Override
    public IceConstantBoolean le(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).le(other);
        } else if (compare > 0) {
            return this.le(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue <= otherValue);
        }
    }

    @Override
    public IceConstantBoolean gt(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).gt(other);
        } else if (compare > 0) {
            return this.gt(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue > otherValue);
        }
    }

    @Override
    public IceConstantBoolean ge(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).ge(other);
        } else if (compare > 0) {
            return this.ge(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue >= otherValue);
        }
    }

    @Override
    public IceConstantBoolean eq(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).eq(other);
        } else if (compare > 0) {
            return this.eq(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue == otherValue);
        }
    }

    @Override
    public IceConstantBoolean ne(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).ne(other);
        } else if (compare > 0) {
            return this.ne(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantFloat) other).getValue();
            return IceConstantData.create(thisValue != otherValue);
        }
    }

    @Override
    public IceConstantBoolean and(IceConstantData other) {
        return this.castTo(IceType.I1).and(other);
    }

    @Override
    public IceConstantBoolean or(IceConstantData other) {
        return this.castTo(IceType.I1).or(other);
    }

    @Override
    public IceConstantBoolean not(IceConstantData other) {
        return this.castTo(IceType.I1).not(other);
    }
}
