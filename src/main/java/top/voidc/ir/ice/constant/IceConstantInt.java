package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

import java.util.Objects;

public class IceConstantInt extends IceConstantData {
    private final int value;

    public IceConstantInt(int value) {
        super(IceType.I32);
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " " : "") + getValue();
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I32 -> this.clone();
            case I1 -> IceConstantData.create(value != 0);
            case F32 -> new IceConstantFloat((float) value);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantInt(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IceConstantInt that)) return false;
        return value == that.value;
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
            // 同类型
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantInt) other).getValue();
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
            final var otherValue = ((IceConstantInt) other).getValue();
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
            final var otherValue = ((IceConstantInt) other).getValue();
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
            final var otherValue = ((IceConstantInt) other).getValue();
            if (otherValue == 0) {
                throw new ArithmeticException("Division by zero");
            }
            return IceConstantData.create(thisValue / otherValue);
        }
    }

    @Override
    public IceConstantData mod(IceConstantData other) {
        Objects.requireNonNull(other);
        final var compare = this.getType().compareTo(other.getType());
        if (compare < 0) {
            return this.castTo(other.getType()).mod(other);
        } else if (compare > 0) {
            return this.mod(other.castTo(this.getType()));
        } else {
            final var thisValue = this.getValue();
            final var otherValue = ((IceConstantInt) other).getValue();
            if (otherValue == 0) {
                throw new ArithmeticException("Division by zero in modulo operation");
            }
            return IceConstantData.create(thisValue % otherValue);
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
            final var otherValue = ((IceConstantInt) other).getValue();
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
            final var otherValue = ((IceConstantInt) other).getValue();
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
            final var otherValue = ((IceConstantInt) other).getValue();
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
            final var otherValue = ((IceConstantInt) other).getValue();
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
            final var otherValue = ((IceConstantInt) other).getValue();
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
            final var otherValue = ((IceConstantInt) other).getValue();
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
