/*
 * Copyright (c) 2011-2019, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.misc.impl;

import boofcv.generate.AutoTypeImage;
import boofcv.generate.CodeGeneratorBase;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import static boofcv.generate.AutoTypeImage.*;


/**
 * Generates functions for ImplPixelMath.
 *
 * @author Peter Abeles
 */
public class GenerateImplPixelMath extends CodeGeneratorBase {

	private AutoTypeImage input;

	@Override
	public void generate() throws FileNotFoundException {
		printPreamble();

		printAbs();
		printNegative();

		List<TwoTemplate> listTwo = new ArrayList<>();
		listTwo.add( new Multiple());
		listTwo.add( new Divide());
		listTwo.add( new Plus());
		listTwo.add( new Minus(true));
		listTwo.add( new Minus(false));

		for( TwoTemplate t : listTwo ) {
			print_img_scalar(t,false);
			print_img_scalar(t,true);
		}

		printAll();
		out.println("}");
	}

	private void printPreamble() {
		out.print("import boofcv.struct.image.*;\n" +
				"\n" +
				"//CONCURRENT_INLINE import boofcv.concurrency.BoofConcurrency;\n" +
				"import boofcv.alg.InputSanityCheck;\n" +
				"import javax.annotation.Generated;\n" +
				"\n" +
				"/**\n" +
				" * Implementation of algorithms in PixelMath\n" +
				" *\n" +
				" * <p>DO NOT MODIFY: Generated by " + getClass().getName() + ".</p>\n" +
				" *\n" +
				" * @author Peter Abeles\n" +
				" */\n" +
				generatedAnnotation() +
				"@SuppressWarnings(\"Duplicates\")\n" +
				"public class " + className + " {\n\n");
	}

	public void printAll() {

		AutoTypeImage types[] = AutoTypeImage.getSpecificTypes();

		for( AutoTypeImage t : types ) {
			input = t;

			printBoundImage();
			printDiffAbs();
		}

		AutoTypeImage outputsAdd[] = new AutoTypeImage[]{U16,S16,S32,S32,S32,S64,F32,F64};
		AutoTypeImage outputsSub[] = new AutoTypeImage[]{I16,S16,S32,S32,S32,S64,F32,F64};

		for( int i = 0; i < types.length; i++ ) {
			printAddTwoImages(types[i],outputsAdd[i]);
			printSubtractTwoImages(types[i],outputsSub[i]);

			if( !types[i].isInteger() ) {
				printMultTwoImages(types[i],types[i]);
				printDivTwoImages(types[i],types[i]);
				printLog(types[i],types[i]);
				printLogSign(types[i], types[i]);
				printPow2(types[i], types[i]);
				printSqrt(types[i], types[i]);
			}
		}
	}

	private void print( String funcName , String operation , AutoTypeImage types[] ) {
		for( AutoTypeImage input : types ) {
			String arrayType = input.getDataType();
			out.println(
					"\tpublic static void "+funcName+"( "+arrayType+"[] input , int inputStart , int inputStride ,\n" +
							"\t\t\t\t\t\t\t   "+arrayType+"[] output , int outputStart , int outputStride ,\n" +
							"\t\t\t\t\t\t\t   int rows , int cols )\n" +
							"\t{\n" +
							"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,rows,y->{\n" +
							"\t\tfor( int y = 0; y < rows; y++ ) {\n" +
							"\t\t\tint indexSrc = inputStart + y*inputStride;\n" +
							"\t\t\tint indexDst = outputStart + y*outputStride;\n" +
							"\t\t\tint end = indexSrc + cols;\n" +
							"\n" +
							"\t\t\tfor( ; indexSrc < end; indexSrc++ , indexDst++) {\n" +
							"\t\t\t\toutput[indexDst] = "+input.getTypeCastFromSum()+operation +";\n" +
							"\t\t\t}\n" +
							"\t\t}\n" +
							"\t\t//CONCURRENT_ABOVE });\n" +
							"\t}\n");
		}
	}

	public void printAbs()
	{
		print("abs","Math.abs(input[indexSrc])",AutoTypeImage.getSigned());
	}

	public void printNegative()
	{
		print("negative","-input[indexSrc]",AutoTypeImage.getSigned());
	}

	private void print_img_scalar( TwoTemplate template , boolean bounded ) {

		String funcName = template.getName();
		String varName = template.getVariableName();

		for( AutoTypeImage t : template.getTypes() ) {
			input = t;
			String variableType;
			if( template.isScaleOp() )
				variableType = input.isInteger() ? "double" : input.getSumType();
			else
				variableType = input.getSumType();

			String funcArrayName = input.isSigned() ? funcName : funcName+"U";
			funcArrayName += template.isImageFirst() ? "_A" : "_B";

			if( bounded ) {
				print_array_scalar_bounded(funcArrayName, variableType, varName, template.getOperation());
			} else {
				print_array_scalar(funcArrayName, variableType, varName, template.getOperation());
			}
		}
	}

	public void print_array_scalar(String funcName , String varType , String varName , String operation  )
	{
		String arrayType = input.getDataType();

		String typeCast = varType.equals(input.getDataType()) ? "" : "("+input.getDataType()+")";

		out.println(
				"\tpublic static void "+funcName+"( "+arrayType+"[] input , int inputStart , int inputStride , \n" +
				"\t\t\t\t\t\t\t   "+varType+" "+varName+" ,\n" +
				"\t\t\t\t\t\t\t   "+arrayType+"[] output , int outputStart , int outputStride ,\n" +
				"\t\t\t\t\t\t\t   int rows , int cols )\n" +
				"\t{\n" +
				"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,rows,y->{\n" +
				"\t\tfor( int y = 0; y < rows; y++ ) {\n" +
				"\t\t\tint indexSrc = inputStart + y*inputStride;\n" +
				"\t\t\tint indexDst = outputStart + y*outputStride;\n" +
				"\t\t\tint end = indexSrc + cols;\n" +
				"\n" +
				"\t\t\tfor( ; indexSrc < end; indexSrc++ , indexDst++) {\n" +
				"\t\t\t\toutput[indexDst] = "+typeCast+operation +";\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t\t//CONCURRENT_ABOVE });\n" +
				"\t}\n");
	}

	public void print_array_scalar_bounded(String funcName , String varType , String varName , String operation  )
	{
		String arrayType = input.getDataType();

		String sumType = input.getSumType();
		String typeCast = varType.equals(sumType) ? "" : "("+sumType+")";

		out.println(
				"\tpublic static void "+funcName+"( "+arrayType+"[] input , int inputStart , int inputStride , \n" +
				"\t\t\t\t\t\t\t   "+varType+" "+varName+" , "+sumType+" lower , "+sumType+" upper ,\n" +
				"\t\t\t\t\t\t\t   "+arrayType+"[] output , int outputStart , int outputStride ,\n" +
				"\t\t\t\t\t\t\t   int rows , int cols )\n" +
				"\t{\n" +
				"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,rows,y->{\n" +
				"\t\tfor( int y = 0; y < rows; y++ ) {\n" +
				"\t\t\tint indexSrc = inputStart + y*inputStride;\n" +
				"\t\t\tint indexDst = outputStart + y*outputStride;\n" +
				"\t\t\tint end = indexSrc + cols;\n" +
				"\n" +
				"\t\t\tfor( ; indexSrc < end; indexSrc++ , indexDst++) {\n" +
				"\t\t\t\t"+sumType+" val = "+typeCast+operation+";\n" +
				"\t\t\t\tif( val < lower ) val = lower;\n" +
				"\t\t\t\tif( val > upper ) val = upper;\n" +
				"\t\t\t\toutput[indexDst] = "+input.getTypeCastFromSum()+"val;\n"+
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t\t//CONCURRENT_ABOVE });\n" +
				"\t}\n");
	}

	public void printBoundImage() {

		String bitWise = input.getBitWise();
		String sumType = input.getSumType();

		out.print(
				"\tpublic static void boundImage( "+input.getSingleBandName()+" img , "+sumType+" min , "+sumType+" max ) {\n" +
				"\t\tfinal int h = img.getHeight();\n" +
				"\t\tfinal int w = img.getWidth();\n" +
				"\n" +
				"\t\t"+input.getDataType()+"[] data = img.data;\n" +
				"\n" +
				"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,h,y->{\n" +
				"\t\tfor (int y = 0; y < h; y++) {\n" +
				"\t\t\tint index = img.getStartIndex() + y * img.getStride();\n" +
				"\t\t\tint indexEnd = index+w;\n" +
				"\t\t\t// for(int x = 0; x < w; x++ ) {\n" +
				"\t\t\tfor (; index < indexEnd; index++) {\n" +
				"\t\t\t\t"+sumType+" value = data[index]"+bitWise+";\n" +
				"\t\t\t\tif( value < min )\n" +
				"\t\t\t\t\tdata[index] = "+input.getTypeCastFromSum()+"min;\n" +
				"\t\t\t\telse if( value > max )\n" +
				"\t\t\t\t\tdata[index] = "+input.getTypeCastFromSum()+"max;\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t\t//CONCURRENT_ABOVE });\n" +
				"\t}\n\n");
	}

	public void printDiffAbs() {
		String bitWise = input.getBitWise();
		String typeCast = input.isInteger() ? "("+input.getDataType()+")" : "";

		out.print(
				"\tpublic static void diffAbs( "+input.getSingleBandName()+" imgA , "+input.getSingleBandName()+" imgB , "+input.getSingleBandName()+" output ) {\n" +
				"\n" +
				"\t\tfinal int h = imgA.getHeight();\n" +
				"\t\tfinal int w = imgA.getWidth();\n" +
				"\n" +
				"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,h,y->{\n" +
				"\t\tfor (int y = 0; y < h; y++) {\n" +
				"\t\t\tint indexA = imgA.getStartIndex() + y * imgA.getStride();\n" +
				"\t\t\tint indexB = imgB.getStartIndex() + y * imgB.getStride();\n" +
				"\t\t\tint indexDiff = output.getStartIndex() + y * output.getStride();\n" +
				"\t\t\t\n" +
				"\t\t\tint indexEnd = indexA+w;\n" +
				"\t\t\t// for(int x = 0; x < w; x++ ) {\n" +
				"\t\t\tfor (; indexA < indexEnd; indexA++, indexB++, indexDiff++ ) {\n" +
				"\t\t\t\toutput.data[indexDiff] = "+typeCast+"Math.abs((imgA.data[indexA] "+bitWise+") - (imgB.data[indexB] "+bitWise+"));\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t\t//CONCURRENT_ABOVE });\n" +
				"\t}\n\n");
	}

	public void printAddTwoImages( AutoTypeImage typeIn , AutoTypeImage typeOut  ) {
		printTwoImageOperation("add",typeIn,typeOut,"+");
	}

	public void printSubtractTwoImages( AutoTypeImage typeIn , AutoTypeImage typeOut ) {
		printTwoImageOperation("subtract",typeIn,typeOut,"-");
	}

	public void printMultTwoImages( AutoTypeImage typeIn , AutoTypeImage typeOut  ) {
		printTwoImageOperation("multiply",typeIn,typeOut,"*");
	}

	public void printDivTwoImages( AutoTypeImage typeIn , AutoTypeImage typeOut  ) {
		printTwoImageOperation("divide",typeIn,typeOut,"/");
	}

	public void printTwoImageOperation( String name , AutoTypeImage typeIn , AutoTypeImage typeOut , String op ) {

		String bitWise = typeIn.getBitWise();
		String typeCast = typeOut.isInteger() ? "("+typeOut.getDataType()+")" : "";

		out.print(
				"\tpublic static void "+name+"( "+typeIn.getSingleBandName()+" imgA , "+typeIn.getSingleBandName()+" imgB , "+typeOut.getSingleBandName()+" output ) {\n" +
						"\n" +
						"\t\tfinal int h = imgA.getHeight();\n" +
						"\t\tfinal int w = imgA.getWidth();\n" +
						"\n" +
						"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,h,y->{\n" +
						"\t\tfor (int y = 0; y < h; y++) {\n" +
						"\t\t\tint indexA = imgA.getStartIndex() + y * imgA.getStride();\n" +
						"\t\t\tint indexB = imgB.getStartIndex() + y * imgB.getStride();\n" +
						"\t\t\tint indexOut = output.getStartIndex() + y * output.getStride();\n" +
						"\t\t\t\n" +
						"\t\t\tint indexEnd = indexA+w;\n" +
						"\t\t\t// for(int x = 0; x < w; x++ ) {\n" +
						"\t\t\tfor (; indexA < indexEnd; indexA++, indexB++, indexOut++ ) {\n" +
						"\t\t\t\toutput.data[indexOut] = "+typeCast+"((imgA.data[indexA] "+bitWise+") "+op+" (imgB.data[indexB] "+bitWise+"));\n" +
						"\t\t\t}\n" +
						"\t\t}\n" +
						"\t\t//CONCURRENT_ABOVE });\n" +
						"\t}\n\n");
	}

	public void printLog( AutoTypeImage typeIn , AutoTypeImage typeOut ) {
		String bitWise = typeIn.getBitWise();
		String typeCast = typeOut != AutoTypeImage.F64 ? "("+typeOut.getDataType()+")" : "";

		out.print(
				"\tpublic static void log( "+typeIn.getSingleBandName()+" input , "+typeOut.getSingleBandName()+" output ) {\n" +
				"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,input.height,y->{\n" +
				"\t\tfor( int y = 0; y < input.height; y++ ) {\n" +
				"\t\t\tint indexSrc = input.startIndex + y* input.stride;\n" +
				"\t\t\tint indexDst = output.startIndex + y* output.stride;\n" +
				"\t\t\tint end = indexSrc + input.width;\n" +
				"\n" +
				"\t\t\tfor( ; indexSrc < end; indexSrc++ , indexDst++) {\n" +
				"\t\t\t\toutput.data[indexDst] = "+typeCast+"Math.log(1 + input.data[indexSrc]"+bitWise+");\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t\t//CONCURRENT_ABOVE });\n" +
				"\t}\n\n");
	}

	public void printLogSign( AutoTypeImage typeIn , AutoTypeImage typeOut ) {
		String sumType = typeIn.getSumType();
		String bitWise = typeIn.getBitWise();
		String typeCast = typeOut != AutoTypeImage.F64 ? "("+typeOut.getDataType()+")" : "";

		out.print(
				"\tpublic static void logSign( "+typeIn.getSingleBandName()+" input , "+typeOut.getSingleBandName()+" output ) {\n" +
						"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,input.height,y->{\n" +
						"\t\tfor( int y = 0; y < input.height; y++ ) {\n" +
						"\t\t\tint indexSrc = input.startIndex + y* input.stride;\n" +
						"\t\t\tint indexDst = output.startIndex + y* output.stride;\n" +
						"\t\t\tint end = indexSrc + input.width;\n" +
						"\n" +
						"\t\t\tfor( ; indexSrc < end; indexSrc++ , indexDst++) {\n" +
						"\t\t\t\t"+sumType+" value = input.data[indexSrc]"+bitWise+";\n" +
						"\t\t\t\tif( value < 0 ) {\n" +
						"\t\t\t\t\toutput.data[indexDst] = "+typeCast+"-Math.log(1 - value);\n" +
						"\t\t\t\t} else {\n" +
						"\t\t\t\t\toutput.data[indexDst] = "+typeCast+"Math.log(1 + value);\n" +
						"\t\t\t\t}\n" +
						"\t\t\t}\n" +
						"\t\t}\n" +
						"\t\t//CONCURRENT_ABOVE });\n" +
						"\t}\n\n");
	}

	public void printPow2( AutoTypeImage typeIn , AutoTypeImage typeOut ) {
		String bitWise = typeIn.getBitWise();

		out.print(
				"\tpublic static void pow2( "+typeIn.getSingleBandName()+" input , "+typeOut.getSingleBandName()+" output ) {\n" +
				"\n" +
				"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,input.height,y->{\n" +
				"\t\tfor( int y = 0; y < input.height; y++ ) {\n" +
				"\t\t\tint indexSrc = input.startIndex + y* input.stride;\n" +
				"\t\t\tint indexDst = output.startIndex + y* output.stride;\n" +
				"\t\t\tint end = indexSrc + input.width;\n" +
				"\n" +
				"\t\t\tfor( ; indexSrc < end; indexSrc++ , indexDst++) {\n" +
				"\t\t\t\t"+typeOut.getDataType()+" v = input.data[indexSrc]"+bitWise+";\n" +
				"\t\t\t\toutput.data[indexDst] = v*v;\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t\t//CONCURRENT_ABOVE });\n" +
				"\t}\n\n");
	}

	public void printSqrt( AutoTypeImage typeIn , AutoTypeImage typeOut ) {
		String bitWise = typeIn.getBitWise();
		String typeCast = typeOut != AutoTypeImage.F64 ? "("+typeOut.getDataType()+")" : "";

		out.print(
				"\tpublic static void sqrt( "+typeIn.getSingleBandName()+" input , "+typeOut.getSingleBandName()+" output ) {\n" +
				"\n" +
				"\t\t//CONCURRENT_BELOW BoofConcurrency.loopFor(0,input.height,y->{\n" +
				"\t\tfor( int y = 0; y < input.height; y++ ) {\n" +
				"\t\t\tint indexSrc = input.startIndex + y* input.stride;\n" +
				"\t\t\tint indexDst = output.startIndex + y* output.stride;\n" +
				"\t\t\tint end = indexSrc + input.width;\n" +
				"\n" +
				"\t\t\tfor( ; indexSrc < end; indexSrc++ , indexDst++) {\n" +
				"\t\t\t\toutput.data[indexDst] = "+typeCast+"Math.sqrt(input.data[indexSrc]"+bitWise+");\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t\t//CONCURRENT_ABOVE });\n" +
				"\t}\n\n");
	}

	class Multiple implements TwoTemplate {

		@Override
		public String getVariableName() { return "value";}

		@Override
		public boolean isScaleOp() { return true; }

		@Override
		public boolean isImageFirst() { return true; }

		@Override
		public AutoTypeImage[] getTypes() { return AutoTypeImage.getSpecificTypes(); }

		@Override
		public String getName() {return "multiply";}

		@Override
		public String getOperation() {
			String round = input.isInteger() ? "Math.round" : "";

			return round+"((input[indexSrc] "+input.getBitWise()+") * value)";
		}
	}

	class Divide implements TwoTemplate {

		@Override public String getVariableName() { return "denominator";}

		@Override public boolean isScaleOp() { return true; }

		@Override public boolean isImageFirst() { return true; }

		@Override public AutoTypeImage[] getTypes() { return AutoTypeImage.getSpecificTypes(); }

		@Override
		public String getName() {return "divide";}

		@Override
		public String getOperation() {
			String round = input.isInteger() ? "Math.round" : "";

			return round+"((input[indexSrc] "+input.getBitWise()+") / denominator)";
		}
	}

	class Plus implements TwoTemplate {

		@Override public String getVariableName() { return "value";}

		@Override public boolean isScaleOp() { return false; }

		@Override public boolean isImageFirst() { return true; }

		@Override public AutoTypeImage[] getTypes() { return AutoTypeImage.getSpecificTypes(); }

		@Override
		public String getName() {return "plus";}

		@Override
		public String getOperation() {
			return "((input[indexSrc] "+input.getBitWise()+") + value)";
		}
	}

	class Minus implements TwoTemplate {

		boolean imageFirst;

		public Minus(boolean imageFirst) {
			this.imageFirst = imageFirst;
		}

		@Override public String getVariableName() { return "value";}

		@Override public boolean isScaleOp() { return false; }

		@Override public boolean isImageFirst() { return imageFirst; }

		@Override public AutoTypeImage[] getTypes() { return AutoTypeImage.getSpecificTypes(); }

		@Override
		public String getName() {return "minus";}

		@Override
		public String getOperation() {
			if( imageFirst )
				return "((input[indexSrc] "+input.getBitWise()+") - value)";
			else
				return "(value - (input[indexSrc] "+input.getBitWise()+"))";
		}
	}

	interface Template {
		String getName();

		String getOperation();
	}

	interface TwoTemplate extends Template {
		String getVariableName();

		boolean isScaleOp();

		boolean isImageFirst();

		AutoTypeImage[] getTypes();
	}

	public static void main( String[] args ) throws FileNotFoundException {
		GenerateImplPixelMath gen = new GenerateImplPixelMath();
		gen.parseArguments(args);
		gen.generate();
	}
}
