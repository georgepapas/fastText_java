package cc.fasttext;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang.Validate;
import org.apache.commons.math3.util.FastMath;

import cc.fasttext.io.FormatUtils;
import com.google.common.primitives.Floats;

/**
 * See <a href='https://github.com/facebookresearch/fastText/blob/master/src/vector.cc'>vector.cc</a> &
 * <a href='https://github.com/facebookresearch/fastText/blob/master/src/vector.h'>vector.h</a>
 */
public class Vector {

    private float[] data_;

    public Vector(int size) {
        this(new float[size]);
    }

    Vector(float[] data) {
        this.data_ = data;
    }

    public int size() {
        return data_.length;
    }

    public float get(int i) {
        return data_[i];
    }

    public void set(int i, float value) {
        data_[i] = value;
    }

    public void compute(int i, DoubleUnaryOperator operator) {
        Objects.requireNonNull(operator, "Null operator");
        data_[i] = (float) operator.applyAsDouble(data_[i]);
    }

    float[] data() {
        return data_;
    }

    /**
     * Returns a fixed-size list backed by the vectors data.
     *
     * @return List of floats
     */
    public List<Float> getData() {
        return Floats.asList(data_);
    }

    public void clear() {
        data_ = new float[data_.length];
    }

    /**
     * <pre>{@code real Vector::norm() const {
     *  real sum = 0;
     *  for (int64_t i = 0; i < m_; i++) {
     *      sum += data_[i] * data_[i];
     *  }
     *  return std::sqrt(sum);
     * }
     * }</pre>
     *
     * @return float
     */
    public float norm() {
        double sum = 0;
        for (int i = 0; i < size(); i++) {
            sum += data_[i] * data_[i];
        }
        return (float) FastMath.sqrt(sum);
    }

    /**
     * <pre>{@code void Vector::addVector(const Vector& source) {
     *  assert(m_ == source.m_);
     *  for (int64_t i = 0; i < m_; i++) {
     *      data_[i] += source.data_[i];
     *  }
     * }}</pre>
     *
     * @param source {@link Vector}
     */
    public void addVector(Vector source) {
        addVector(source, 1);
    }

    /**
     * Sums up the vector with another one and some multiplier.
     * <pre>{@code void Vector::addVector(const Vector& source, real s) {
     *  assert(m_ == source.m_);
     *  for (int64_t i = 0; i < m_; i++) {
     *      data_[i] += s * source.data_[i];
     *  }
     * }}</pre>
     *
     * @param source {@link Vector}
     * @param s      float (see real.h)
     */
    public void addVector(Vector source, float s) {
        Validate.isTrue(size() == Objects.requireNonNull(source, "Null source vector").size(), "Wrong size of vector: " + size() + "!=" + source.size());
        for (int i = 0; i < size(); i++) {
            data_[i] += s * source.data_[i];
        }
    }

    /**
     * <pre>{@code
     * void Vector::mul(real a) {
     *  for (int64_t i = 0; i < m_; i++) {
     *      data_[i] *= a;
     *  }
     * }}</pre>
     *
     * @param a float
     */
    public void mul(float a) {
        for (int i = 0; i < size(); i++) {
            data_[i] *= a;
        }
    }

    /**
     * <pre>{@code
     * void Vector::addRow(const Matrix& A, int64_t i) {
     *  assert(i >= 0);
     *  assert(i < A.m_);
     *  assert(m_ == A.n_);
     *  for (int64_t j = 0; j < A.n_; j++) {
     *      data_[j] += A.at(i, j);
     *  }
     * }}</pre>
     *
     * @param matrix {@link Matrix}
     * @param i      (int64_t originally) matrix row num (m-dimension)
     */
    public void addRow(Matrix matrix, int i) {
        Validate.isTrue(i >= 0 && i < matrix.getM(), "Incompatible index (" + i + ") and matrix m-size (" + matrix.getM() + ")");
        Validate.isTrue(size() == matrix.getN(), "Wrong matrix n-size: " + size() + " != " + matrix.getN());
        if (matrix.isQuant()) {
            addRow((QMatrix) matrix, i);
            return;
        }
        for (int j = 0; j < matrix.getN(); j++) {
            data_[j] += matrix.at(i, j);
        }
    }

    /**
     * <pre>{@code
     * void Vector::addRow(const QMatrix& A, int64_t i) {
     *  assert(i >= 0);
     *  A.addToVector(*this, i);
     * }}</pre>
     *
     * @param matrix {@link QMatrix}
     * @param i      (int64_t originally) matrix row num (m-dimension)
     */
    private void addRow(QMatrix matrix, int i) {
        Validate.isTrue(i >= 0);
        matrix.addToVector(this, i);
    }

    /**
     * <pre>{@code
     * void Vector::addRow(const Matrix& A, int64_t i, real a) {
     *  assert(i >= 0);
     *  assert(i < A.m_);
     *  assert(m_ == A.n_);
     *  for (int64_t j = 0; j < A.n_; j++) {
     *      data_[j] += a * A.at(i, j);
     *  }
     * }
     * }</pre>
     *
     * @param matrix {@link Matrix}
     * @param i      m-dimension matrix coordinate
     * @param a      float, multiplier
     */
    public void addRow(Matrix matrix, int i, float a) {
        Validate.isTrue(i >= 0 && i < matrix.getM(), "Incompatible index (" + i + ") and matrix m-size (" + matrix.getM() + ")");
        Validate.isTrue(size() == matrix.getN(), "Wrong matrix n-size: " + size() + " != " + matrix.getN());
        for (int j = 0; j < matrix.getN(); j++) {
            data_[j] += a * matrix.at(i, j);
        }
    }

    /**
     * <pre>{@code
     * void Vector::mul(const Matrix& A, const Vector& vec) {
     *  assert(A.m_ == m_);
     *  assert(A.n_ == vec.m_);
     *  for (int64_t i = 0; i < m_; i++) {
     *      data_[i] = A.dotRow(vec, i);
     *  }
     * }}</pre>
     *
     * @param matrix {@link Matrix}
     * @param vector {@link Vector}
     */
    public void mul(Matrix matrix, Vector vector) {
        Validate.isTrue(matrix.getM() == size(), "Wrong matrix m-size: " + size() + " != " + matrix.getM());
        Validate.isTrue(matrix.getN() == vector.size(), "Matrix n-size (" + matrix.getN() + ") and vector size (" + vector.size() + ")  are not equal.");
        for (int i = 0; i < size(); i++) {
            data_[i] = matrix.dotRow(vector, i);
        }
    }

    /**
     * <pre>{@code int64_t Vector::argmax() {
     *  real max = data_[0];
     *  int64_t argmax = 0;
     *  for (int64_t i = 1; i < m_; i++) {
     *      if (data_[i] > max) {
     *          max = data_[i];
     *          argmax = i;
     *  }
     * }
     * return argmax;
     * }}</pre>
     *
     * @return int (original : int64_t)
     */
    public int argmax() {
        float max = get(0);
        int argmax = 0;
        for (int i = 1; i < size(); i++) {
            if (get(i) <= max) {
                continue;
            }
            max = get(i);
            argmax = i;
        }
        return argmax;
    }

    /**
     * Returns a string representation of the vector.
     * Used while printing to console and save vectors to file.
     *
     * @return vector as String
     * @see FormatUtils#toString(float)
     */
    @Override
    public String toString() {
        return getData().stream().map(FormatUtils::toString).collect(Collectors.joining(" "));
    }

}
