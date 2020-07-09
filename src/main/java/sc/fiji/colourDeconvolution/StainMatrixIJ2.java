package sc.fiji.colourDeconvolution;

import net.imagej.ImgPlus;
import net.imagej.ops.create.img.Imgs;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.ColorChannelOrder;
import net.imglib2.converter.Converters;
import net.imglib2.display.ColorTable8;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.GenericComposite;
import net.imglib2.view.composite.NumericComposite;

public class StainMatrixIJ2 extends StainMatrixBase {
    /**
     * Compute the Deconvolution images and return an ImgPlus array of three 8-bit
     * images. If the specimen is stained with a 2 colour scheme (such as H &amp;
     * E) the 3rd image represents the complimentary of the first two colours
     * (i.e. green).
     *
     * @param imp : The ImagePlus that will be deconvolved. RGB only.
     * @return a Stack array of three 8-bit images
     */
    public ImgPlus<UnsignedByteType>[] compute(ImgPlus<UnsignedByteType> imp) {
        double[] q = initComputation(true);

        // TODO For the algorithm, we don't actually need an ImgPlus but only an Img
        Img<UnsignedByteType> img = imp.getImg();

        int width = (int) img.dimension(0);
        int height = (int) img.dimension(1);

        double log255 = Math.log(255.0);
        
        // Convert img to a composite
        RandomAccessibleInterval<NumericComposite<UnsignedByteType>> collapseNumeric = Views.collapseNumeric(img);
        RandomAccessibleInterval<ARGBType> mergeARGB = Converters.mergeARGB(img, ColorChannelOrder.RGB);
        
        // Create output image of same type
        Img<UnsignedByteType> outputImg = img.factory().create(img);
        RandomAccessibleInterval<ARGBType> outputRAI = Converters.mergeARGB(outputImg, ColorChannelOrder.RGB);
                
        LoopBuilder.setImages(mergeARGB, outputRAI).forEachPixel(
        	(i, o) -> {
        		int rgba = i.get();
        		int R = ARGBType.red(rgba);
        		int G = ARGBType.green(rgba);
        		int B = ARGBType.blue(rgba);
        		
        		double Rlog = -((255.0 * Math.log(((double) R + 1) / 255.0)) / log255);
                double Glog = -((255.0 * Math.log(((double) G + 1) / 255.0)) / log255);
                double Blog = -((255.0 * Math.log(((double) B + 1) / 255.0)) / log255);
                
                int[] channels = new int[3];
                for (int channel = 0; channel < 3; channel++) {
                    // Rescale to match original paper values
                    double Rscaled = Rlog * q[channel * 3];
                    double Gscaled = Glog * q[channel * 3 + 1];
                    double Bscaled = Blog * q[channel * 3 + 2];
                    double output = Math.exp(-((Rscaled + Gscaled + Bscaled) - 255.0) * log255 / 255.0);
                    if (output > 255) output = 255;

                    // TODO Check conversion is correct
                    channels[channel] = (int) (Math.floor(output + .5));
                }
                
                o.set(ARGBType.rgba(channels[0], channels[1], channels[2], 0));
        	}
        );
        
        // Convert outputRAI from composite to multichannel image
        RandomAccessibleInterval<UnsignedByteType> argbChannels = Converters.argbChannels(outputRAI);
        RandomAccessibleInterval<UnsignedByteType> hyperSlice = Views.hyperSlice(argbChannels, 2, 0);
        
        // TODO Optimize below
        @SuppressWarnings("unchecked")
        ImgPlus<UnsignedByteType>[] outputImages = new ImgPlus[3];
        outputImages[0] = new ImgPlus<>((RandomAccessibleInterval<UnsignedByteType>) Views.hyperSlice(argbChannels, 2, 0));
        outputImages[1] = new ImgPlus<>(ArrayImgs.unsignedBytes(newpixels[1], width, height));
        outputImages[2] = new ImgPlus<>(ArrayImgs.unsignedBytes(newpixels[2], width, height));
        initializeColorTables(outputImages);
        return outputImages;
    }

    private ImgPlus<UnsignedByteType>[] initializeColorTables(ImgPlus<UnsignedByteType>[] outputImages) {
        byte[] rLUT = new byte[256];
        byte[] gLUT = new byte[256];
        byte[] bLUT = new byte[256];

        for (int channel = 0; channel < 3; channel++) {
            for (int j = 0; j < 256; j++) { //LUT[1]
                rLUT[255 - j] = (byte) (255.0 - (double) j * cosx[channel]);
                gLUT[255 - j] = (byte) (255.0 - (double) j * cosy[channel]);
                bLUT[255 - j] = (byte) (255.0 - (double) j * cosz[channel]);
            }
            outputImages[channel].initializeColorTables(3);
            outputImages[channel].setColorTable(new ColorTable8(rLUT), 0);
            outputImages[channel].setColorTable(new ColorTable8(gLUT), 1);
            outputImages[channel].setColorTable(new ColorTable8(bLUT), 2);
        }
        return outputImages;
    }
}
