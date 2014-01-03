package com.ccc.deeplearning.nn.matrix.jblas;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;

import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.jblas.DoubleMatrix;

import com.ccc.deeplearning.dbn.matrix.jblas.DBN;
import com.ccc.deeplearning.optimize.NeuralNetworkOptimizer;

import static com.ccc.deeplearning.util.MatrixUtil.*;

/**
 * Baseline class for any Neural Network used
 * as a layer in a deep network such as an {@link DBN}
 * @author Adam Gibson
 *
 */
public abstract class BaseNeuralNetwork implements NeuralNetwork,Persistable {

	private static final long serialVersionUID = -7074102204433996574L;
	/* Number of visible inputs */
	public int nVisible;
	/**
	 * Number of hidden units
	 * One tip with this is usually having
	 * more hidden units than inputs (read: input rows here)
	 * will typically cause terrible overfitting.
	 * 
	 * Another rule worthy of note: more training data typically results
	 * in more redundant data. It is usually a better idea to use a smaller number
	 * of hidden units.
	 *  
	 *  
	 *   
	 **/
	public int nHidden;
	/* Weight matrix */
	public DoubleMatrix W;
	/* hidden bias */
	public DoubleMatrix hBias;
	/* visible bias */
	public DoubleMatrix vBias;
	/* RNG for sampling. */
	public RandomGenerator rng;
	/* input to the network */
	public DoubleMatrix input;
	/* sparsity target */
	public double sparsity = 0.01;
	/* momentum for learning */
	public double momentum = 0.1;
	/* L2 Regularization constant */
	public double l2 = 0.1;
	public NeuralNetworkOptimizer optimizer;
	

	public BaseNeuralNetwork() {}
	/**
	 * 
	 * @param nVisible the number of outbound nodes
	 * @param nHidden the number of nodes in the hidden layer
	 * @param W the weights for this vector, maybe null, if so this will
	 * create a matrix with nHidden x nVisible dimensions.
	 * @param hBias the hidden bias
	 * @param vBias the visible bias (usually b for the output layer)
	 * @param rng the rng, if not a seed of 1234 is used.
	 */
	public BaseNeuralNetwork(int nVisible, int nHidden, 
			DoubleMatrix W, DoubleMatrix hbias, DoubleMatrix vbias, RandomGenerator rng) {
		this.nVisible = nVisible;
		this.nHidden = nHidden;

		if(rng == null)	
			this.rng = new MersenneTwister(1234);

		else 
			this.rng = rng;

		if(W == null) {
			double a = 1.0 / (double) nVisible;
			/*
			 * Initialize based on the number of visible units..
			 * The lower bound is called the fan in
			 * The outer bound is called the fan out.
			 * 
			 * Below's advice works for Denoising AutoEncoders and other 
			 * neural networks you will use due to the same baseline guiding principles for
			 * both RBMs and Denoising Autoencoders.
			 * 
			 * Hinton's Guide to practical RBMs:
			 * The weights are typically initialized to small random values chosen from a zero-mean Gaussian with
			 * a standard deviation of about 0.01. Using larger random values can speed the initial learning, but
			 * it may lead to a slightly worse final model. Care should be taken to ensure that the initial weight
			 * values do not allow typical visible vectors to drive the hidden unit probabilities very close to 1 or 0
			 * as this significantly slows the learning.
			 */
			UniformRealDistribution u = new UniformRealDistribution(rng,-a,a);

			this.W = DoubleMatrix.zeros(nVisible,nHidden);

			for(int i = 0; i < this.W.rows; i++) {
				for(int j = 0; j < this.W.columns; j++) 
					this.W.put(i,j,u.sample());

			}


		}
		else	
			this.W = W;


		if(hbias == null) 
			this.hBias = DoubleMatrix.zeros(nHidden);

		else if(hbias.length != nHidden)
			throw new IllegalArgumentException("Hidden bias must have a length of " + nHidden + " length was " + hbias.length);

		else
			this.hBias = hbias;

		if(vbias == null) 
			this.vBias = DoubleMatrix.zeros(nVisible);

		else if(vbias.length != nVisible) 
			throw new IllegalArgumentException("Visible bias must have a length of " + nVisible + " but length was " + vbias.length);

		else 
			this.vBias = vbias;
	}

	@Override
	public void merge(NeuralNetwork network,int batchSize) {
		W.addi(network.getW().mini(W).div(batchSize));
		hBias.addi(network.gethBias().mini(hBias).div(batchSize));
		vBias.addi(network.getvBias().mini(vBias).div(batchSize));
	}


	/**
	 * Regularize weights or weight averaging.
	 * This accounts for momentum, sparsity target,
	 * and batch size
	 * @param batchSize the batch size of the recent training set
	 * @param lr the learning rate
	 */
	public void regularizeWeights(int batchSize,double lr) {
		if(batchSize < 1)
			throw new IllegalArgumentException("Batch size must be at least 1");
		this.W = W.div(batchSize).mul(1 - momentum).add(W.min(W.mul(l2)));
	}
	
	/**
	 * 
	 * @param input the input examples
	 * @param nVisible the number of outbound nodes
	 * @param nHidden the number of nodes in the hidden layer
	 * @param W the weights for this vector, maybe null, if so this will
	 * create a matrix with nHidden x nVisible dimensions.
	 * @param hBias the hidden bias
	 * @param vBias the visible bias (usually b for the output layer)
	 * @param rng the rng, if not a seed of 1234 is used.
	 */
	public BaseNeuralNetwork(DoubleMatrix input, int n_visible, int n_hidden, 
			DoubleMatrix W, DoubleMatrix hbias, DoubleMatrix vbias, RandomGenerator rng) {
		this(n_visible,n_hidden,W,hbias,vbias,rng);
		this.input = input;
	}


	/**
	 * Copies params from the passed in network
	 * to this one
	 * @param n the network to copy
	 */
	public void update(BaseNeuralNetwork n) {
		this.W = n.W;
		this.hBias = n.hBias;
		this.vBias = n.vBias;
		this.l2 = n.l2;
		this.momentum = n.momentum;
		this.nHidden = n.nHidden;
		this.nVisible = n.nVisible;
		this.rng = n.rng;
		this.sparsity = n.sparsity;
	}
	
	/**
	 * Load (using {@link ObjectInputStream}
	 * @param is the input stream to load from (usually a file)
	 */
	public void load(InputStream is) {
		try {
			ObjectInputStream ois = new ObjectInputStream(is);
			BaseNeuralNetwork loaded = (BaseNeuralNetwork) ois.readObject();
			update(loaded);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	
	/**
	 * Reconstruction error.
	 * @return reconstruction error
	 */
	public double getReConstructionCrossEntropy() {
		DoubleMatrix preSigH = input.mmul(W).addRowVector(hBias);
		DoubleMatrix sigH = sigmoid(preSigH);

		DoubleMatrix preSigV = sigH.mmul(W.transpose()).addRowVector(vBias);
		DoubleMatrix sigV = sigmoid(preSigV);
		DoubleMatrix inner = 
				input.mul(log(sigV))
				.add(oneMinus(input)
						.mul(log(oneMinus(sigV))));
		
		return - inner.rowSums().mean();
	}


	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#getnVisible()
	 */
	@Override
	public int getnVisible() {
		return nVisible;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#setnVisible(int)
	 */
	@Override
	public void setnVisible(int nVisible) {
		this.nVisible = nVisible;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#getnHidden()
	 */
	@Override
	public int getnHidden() {
		return nHidden;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#setnHidden(int)
	 */
	@Override
	public void setnHidden(int nHidden) {
		this.nHidden = nHidden;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#getW()
	 */
	@Override
	public DoubleMatrix getW() {
		return W;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#setW(org.jblas.DoubleMatrix)
	 */
	@Override
	public void setW(DoubleMatrix w) {
		W = w;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#gethBias()
	 */
	@Override
	public DoubleMatrix gethBias() {
		return hBias;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#sethBias(org.jblas.DoubleMatrix)
	 */
	@Override
	public void sethBias(DoubleMatrix hBias) {
		this.hBias = hBias;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#getvBias()
	 */
	@Override
	public DoubleMatrix getvBias() {
		return vBias;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#setvBias(org.jblas.DoubleMatrix)
	 */
	@Override
	public void setvBias(DoubleMatrix vBias) {
		this.vBias = vBias;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#getRng()
	 */
	@Override
	public RandomGenerator getRng() {
		return rng;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#setRng(org.apache.commons.math3.random.RandomGenerator)
	 */
	@Override
	public void setRng(RandomGenerator rng) {
		this.rng = rng;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#getInput()
	 */
	@Override
	public DoubleMatrix getInput() {
		return input;
	}

	/* (non-Javadoc)
	 * @see com.ccc.deeplearning.nn.matrix.jblas.NeuralNetwork#setInput(org.jblas.DoubleMatrix)
	 */
	@Override
	public void setInput(DoubleMatrix input) {
		this.input = input;
	}


	public double getSparsity() {
		return sparsity;
	}
	public void setSparsity(double sparsity) {
		this.sparsity = sparsity;
	}
	public double getMomentum() {
		return momentum;
	}
	public void setMomentum(double momentum) {
		this.momentum = momentum;
	}
	public double getL2() {
		return l2;
	}
	public void setL2(double l2) {
		this.l2 = l2;
	}
	
	/**
	 * Write this to an object output stream
	 * @param os the output stream to write to
	 */
	public void write(OutputStream os) {
		try {
			ObjectOutputStream os2 = new ObjectOutputStream(os);
			os2.writeObject(this);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * All neural networks are based on this idea of 
	 * minimizing reconstruction error.
	 * Both RBMs and Denoising AutoEncoders
	 * have a component for reconstructing, ala different implementations.
	 *  
	 * @param x the input to reconstruct
	 * @return the reconstructed input
	 */
	public abstract DoubleMatrix reconstruct(DoubleMatrix x);
	
	/**
	 * The loss function (cross entropy, reconstruction error,...)
	 * @return the loss function
	 */
	public abstract double lossFunction(Object[] params);

	public double lossFunction() {
		return lossFunction(null);
	}
	
	/**
	 * Train one iteration of the network
	 * @param input the input to train on
	 * @param lr the learning rate to train at
	 * @param params the extra params (k, corruption level,...)
	 */
	public abstract void train(DoubleMatrix input,double lr,Object[] params);
	
	


	public static class Builder<E extends BaseNeuralNetwork> {
		private E ret = null;
		private DoubleMatrix W;
		protected Class<? extends NeuralNetwork> clazz;
		private DoubleMatrix vBias;
		private DoubleMatrix hBias;
		private int numVisible;
		private int numHidden;
		private RandomGenerator gen;
		private DoubleMatrix input;
		private double sparsity = 0.01;
		private double l2 = 0.01;
		private double momentum = 0.1;
		
		public Builder<E> withL2(double l2) {
			this.l2 = l2;
			return this;
		}
		
		
		@SuppressWarnings("unchecked")
		public E buildEmpty() {
			try {
				return (E) clazz.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		
		public Builder<E> withClazz(Class<? extends BaseNeuralNetwork> clazz) {
			this.clazz = clazz;
			return this;
		}
		
		public Builder<E> withSparsity(double sparsity) {
			this.sparsity = sparsity;
			return this;
		}
		public Builder<E> withMomentum(double momentum) {
			this.momentum = momentum;
			return this;
		}
		
		public Builder<E> withInput(DoubleMatrix input) {
			this.input = input;
			return this;
		}

		public Builder<E> asType(Class<E> clazz) {
			this.clazz = clazz;
			return this;
		}


		public Builder<E> withWeights(DoubleMatrix W) {
			this.W = W;
			return this;
		}

		public Builder<E> withVisibleBias(DoubleMatrix vBias) {
			this.vBias = vBias;
			return this;
		}

		public Builder<E> withHBias(DoubleMatrix hBias) {
			this.hBias = hBias;
			return this;
		}

		public Builder<E> numberOfVisible(int numVisible) {
			this.numVisible = numVisible;
			return this;
		}

		public Builder<E> numHidden(int numHidden) {
			this.numHidden = numHidden;
			return this;
		}

		public Builder<E> withRandom(RandomGenerator gen) {
			this.gen = gen;
			return this;
		}

		public E build() {
			if(input != null) 
				return buildWithInput();
			else 
				return buildWithoutInput();
		}

		@SuppressWarnings("unchecked")
		private  E buildWithoutInput() {
			Constructor<?>[] c = clazz.getDeclaredConstructors();
			for(int i = 0; i < c.length; i++) {
				Constructor<?> curr = c[i];
				Class<?>[] classes = curr.getParameterTypes();

				//input matrix found
				if(classes.length > 0 && classes[0].isAssignableFrom(Integer.class) || classes[0].isPrimitive()) {
					try {
						ret = (E) curr.newInstance(numVisible, numHidden, 
								W, hBias,vBias, gen);
						return ret;
					}catch(Exception e) {
						throw new RuntimeException(e);
					}

				}
			}
			return ret;
		}


		@SuppressWarnings("unchecked")
		private  E buildWithInput()  {
			Constructor<?>[] c = clazz.getDeclaredConstructors();
			for(int i = 0; i < c.length; i++) {
				Constructor<?> curr = c[i];
				Class<?>[] classes = curr.getParameterTypes();
				//input matrix found
				if(classes.length > 0 && classes[0].isAssignableFrom(DoubleMatrix.class)) {
					try {
						ret = (E) curr.newInstance(numVisible, numHidden, 
								W, hBias,vBias, gen);
						ret.sparsity = this.sparsity;
						ret.l2 = this.l2;
						ret.momentum = this.momentum;
						return ret;
					}catch(Exception e) {
						throw new RuntimeException(e);
					}

				}
			}
			return ret;
		}
	}

}
