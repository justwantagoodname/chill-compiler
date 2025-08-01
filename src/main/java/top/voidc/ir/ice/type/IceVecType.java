package top.voidc.ir.ice.type;

public class IceVecType extends IceType {

    public static final IceVecType VEC128 = new IceVecType(128); // 128-bit vector with 4 i32 elements

    private final int width;
    private final IceType scalarType;
    private final int vecBitWidth; // Bitwidth of the vector, e.g., 128 for VEC128

    public IceVecType(int vecBitWidth) {
        super(TypeEnum.VEC);
        this.vecBitWidth = vecBitWidth;
        this.width = -1;
        this.scalarType = IceType.ANY;
    }

    public IceVecType(IceType scalarType, int width) {
        super(TypeEnum.VEC);
        assert !scalarType.isAny();
        this.width = width;
        this.scalarType = scalarType;
        this.vecBitWidth = width * scalarType.getByteSize() * 8; // Calculate vector bitwidth
    }

    public int getWidth() {
        return width;
    }

    public IceType getScalarType() {
        return scalarType;
    }


    @Override
    public int getByteSize() {
        if (scalarType.isAny()) return vecBitWidth / 8;
        return width * scalarType.getByteSize();
    }

    @Override
    public String toString() {
        return "<" + width + " x " + scalarType + ">";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IceVecType other)) return false;
        return width == other.width && scalarType.equals(other.scalarType);
    }

    @Override
    public int hashCode() {
        int result = scalarType.hashCode();
        result = 31 * result + width;
        return result;
    }
}
