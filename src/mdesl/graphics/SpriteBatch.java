/**
 * Copyright (c) 2012, Matt DesLauriers All rights reserved.
 *
 *	Redistribution and use in source and binary forms, with or without
 *	modification, are permitted provided that the following conditions are met:
 *
 *	* Redistributions of source code must retain the above copyright notice, this
 *	  list of conditions and the following disclaimer.
 *
 *	* Redistributions in binary
 *	  form must reproduce the above copyright notice, this list of conditions and
 *	  the following disclaimer in the documentation and/or other materials provided
 *	  with the distribution.
 *
 *	* Neither the name of the Matt DesLauriers nor the names
 *	  of his contributors may be used to endorse or promote products derived from
 *	  this software without specific prior written permission.
 *
 *	THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *	AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *	IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *	ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 *	LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *	CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *	SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *	INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *	CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *	ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *	POSSIBILITY OF SUCH DAMAGE.
 */
package mdesl.graphics;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL20.glUniform1i;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;

import mdesl.graphics.glutils.ShaderProgram;
import mdesl.graphics.glutils.VertexArray;
import mdesl.graphics.glutils.VertexAttrib;
import mdesl.graphics.glutils.VertexData;
import mdesl.util.MathUtil;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;

/** @author Matt (mdesl) DesLauriers
 * @author matheusdev */
public class SpriteBatch {
	public static final String U_TEXTURE = "u_texture";
	public static final String U_PROJ_VIEW = "u_projView";

	public static final String ATTR_COLOR = "Color";
	public static final String ATTR_POSITION = "Position";
	public static final String ATTR_TEXCOORD = "TexCoord";

	public static final String DEFAULT_VERT_SHADER = "uniform mat4 " + U_PROJ_VIEW + ";\n"
			+ "attribute vec4 " + ATTR_COLOR + ";\n" + "attribute vec2 " + ATTR_TEXCOORD + ";\n"
			+ "attribute vec2 " + ATTR_POSITION + ";\n" + "varying vec4 vColor;\n"
			+ "varying vec2 vTexCoord; \n" + "void main() {\n" + "	vColor = " + ATTR_COLOR + ";\n"
			+ "	vTexCoord = " + ATTR_TEXCOORD + ";\n" + "	gl_Position = " + U_PROJ_VIEW
			+ " * vec4(" + ATTR_POSITION + ".xy, 0.0, 1.0);\n" + "}";

	public static final String DEFAULT_FRAG_SHADER = "uniform sampler2D " + U_TEXTURE + ";\n"
			+ "varying vec4 vColor;\n" + "varying vec2 vTexCoord;\n" + "void main() {\n"
			+ "	vec4 texColor = texture2D(" + U_TEXTURE + ", vTexCoord);\n"
			+ "	gl_FragColor = vColor * texColor;\n" + "}";

	public static final List<VertexAttrib> ATTRIBUTES = Arrays.asList(new VertexAttrib(0,
			ATTR_POSITION, 2), new VertexAttrib(1, ATTR_COLOR, 4), new VertexAttrib(2,
			ATTR_TEXCOORD, 2));

	static ShaderProgram defaultShader;
	public static int renderCalls = 0;

	protected FloatBuffer buf16;
	protected Matrix4f projMatrix;
	protected Matrix4f viewMatrix;
	protected Matrix4f projViewMatrix;
	protected Matrix4f transpositionPool;

	protected Texture texture;
	protected ShaderProgram program;

	protected VertexData data;

	private int idx;
	private int maxIndex;

	private Color color = new Color();
	private boolean drawing = false;
	
	static ShaderProgram getDefaultShader() throws LWJGLException {
		return defaultShader == null ? new ShaderProgram(DEFAULT_VERT_SHADER, DEFAULT_FRAG_SHADER,
				ATTRIBUTES) : defaultShader;
	}
	
	public SpriteBatch(ShaderProgram program) {
		this(program, 1000);
	}

	public SpriteBatch(ShaderProgram program, int size) {
		this.program = program;

		// later we can do some abstraction to replace this with VBOs...
		this.data = new VertexArray(size * 6, ATTRIBUTES);

		// max indices before we need to flush the renderer
		maxIndex = size * 6;

		viewMatrix = new Matrix4f();

		// default size
		resize(Display.getWidth(), Display.getHeight());
	}

	public SpriteBatch(int size) throws LWJGLException {
		this(getDefaultShader(), size);
	}

	public SpriteBatch() throws LWJGLException {
		this(1000);
	}

	public Matrix4f getViewMatrix() {
		return viewMatrix;
	}

	public Matrix4f getProjectionMatrix() {
		return projMatrix;
	}
	
	/** A convenience method to resize the projection matrix to the given
	 * dimensions, using y-down ortho 2D. This will invoke a call to
	 * updateMatrices.
	 * 
	 * @param width
	 * @param height */
	public void resize(int width, int height) {
		projMatrix = MathUtil.toOrtho2D(projMatrix, 0, 0, Display.getWidth(), Display.getHeight());
		updateUniforms();
	}

	/** Sets this SpriteBatch's color to the RGBA values of the given color
	 * object.
	 * 
	 * @param color the RGBA values to use */
	public void setColor(Color color) {
		setColor(color.r, color.g, color.b, color.a);
	}

	/** Sets this SpriteBatch's color to the given RGBA values.
	 * 
	 * @param r the red value
	 * @param g the green value
	 * @param b the blue value
	 * @param a the alpha value */
	public void setColor(float r, float g, float b, float a) {
		color.set(r, g, b, a);
	}

	/** Call to multiply the the projection with the view matrix and save the
	 * result in the uniform mat4 {@value #U_PROJ_VIEW}, as well as update the
	 * {@value #U_TEXTURE} uniform. */
	public void updateUniforms() {
		updateUniforms(program);
	}

	/** Call to multiply the the projection with the view matrix and save the
	 * result in the uniform mat4 {@value #U_PROJ_VIEW}, as well as update the
	 * {@value #U_TEXTURE} uniform. */
	public void updateUniforms(ShaderProgram program) {
		// Multiply the transposed projection matrix with the view matrix:
		projViewMatrix = Matrix4f.mul(Matrix4f.transpose(projMatrix, transpositionPool),
				viewMatrix, projViewMatrix);

		// bind the program before sending uniforms
		program.use();
		
		boolean oldStrict = ShaderProgram.isStrictMode();
		
		//disable strict mode so we don't run into any problems
		ShaderProgram.setStrictMode(false);
		
		//we can now utilize ShaderProgram's hash map which may be better than glGetUniformLocation
		
		// Store the the multiplied matrix in the "projViewMatrix"-uniform:
		program.setUniformMatrix(U_PROJ_VIEW, false, projViewMatrix);

		// upload texcoord 0
		program.setUniformi(U_TEXTURE, 0);
		
		//reset strict mode
		ShaderProgram.setStrictMode(oldStrict);
	}

	/** An advanced call that allows you to change the shader without uploading
	 * shader uniforms. This will flush the batch if we are within begin(). 
	 * 
	 * @param program
	 * @param updateUniforms whether to call updateUniforms after changing the
	 * programs */
	public void setShader(ShaderProgram program, boolean updateUniforms) {
		if (drawing) {
			flush();
			this.program.use();
		}
		this.program = program;
		if (updateUniforms)
			updateUniforms();
	}

	/** Changes the shader and updates it with the current texture and projView
	 * uniforms. This will flush the batch if we are within begin().
	 * 
	 * @param program the new program to use */
	public void setShader(ShaderProgram program) {
		setShader(program, true);
	}

	public void begin() {
		if (drawing)
			throw new IllegalStateException("must not be drawing before calling begin()");
		drawing = true;
		program.use();
		idx = 0;
		renderCalls = 0;
		texture = null;
	}

	public void end() {
		if (!drawing)
			throw new IllegalStateException("must be drawing before calling end()");
		drawing = false;
		flush();
	}

	public void flush() {
		if (idx > 0) {
			data.flip();
			render();
			idx = 0;
			data.clear();
		}
	}

	public void drawRegion(Texture tex, float srcX, float srcY, float srcWidth, float srcHeight,
			float dstX, float dstY) {
		drawRegion(tex, srcX, srcY, srcWidth, srcHeight, dstX, dstY, srcWidth, srcHeight);
	}

	public void drawRegion(Texture tex, float srcX, float srcY, float srcWidth, float srcHeight,
			float dstX, float dstY, float dstWidth, float dstHeight) {
		float u = srcX / tex.width;
		float v = srcY / tex.height;
		float u2 = (srcX + srcWidth) / tex.width;
		float v2 = (srcY + srcHeight) / tex.height;
		draw(tex, dstX, dstY, dstWidth, dstHeight, u, v, u2, v2);
	}

	public void draw(Texture tex, float x, float y) {
		draw(tex, x, y, tex.width, tex.height);
	}

	public void draw(Texture tex, float x, float y, float width, float height) {
		draw(tex, x, y, width, height, 0, 0, 1, 1);
	}

	public void draw(Texture tex, float x, float y, float width, float height, float u, float v,
			float u2, float v2) {
		checkFlush(tex);
		final float r = color.r;
		final float g = color.g;
		final float b = color.b;
		final float a = color.a;
		
		// top left, top right, bottom left
		vertex(x, y, r, g, b, a, u, v);
		vertex(x + width, y, r, g, b, a, u2, v);
		vertex(x, y + height, r, g, b, a, u, v2);

		// top right, bottom right, bottom left
		vertex(x + width, y, r, g, b, a, u2, v);
		vertex(x + width, y + height, r, g, b, a, u2, v2);
		vertex(x, y + height, r, g, b, a, u, v2);
	}

	/** Renders a texture using custom vertex attributes; e.g. for different
	 * vertex colours. This will ignore the current batch color and "x/y translation".
	 * 
	 * @param tex the texture to use
	 * @param vertices an array of 6 vertices, each holding 8 attributes (total
	 * = 48 elements)
	 * @param offset the offset from the vertices array to start from */
	public void draw(Texture tex, float[] vertices, int offset) {
		checkFlush(tex);
		data.put(vertices, offset, data.getTotalNumComponents() * 6);
		idx += 6;
	}

	VertexData vertex(float x, float y, float r, float g, float b, float a, float u, float v) {
		data.put(x).put(y).put(r).put(g).put(b).put(a).put(u).put(v);
		idx++;
		return data;
	}

	protected void checkFlush(Texture texture) {
		if (texture == null)
			throw new NullPointerException("null texture");

		// we need to bind a different texture/type. this is
		// for convenience; ideally the user should order
		// their rendering wisely to minimize texture binds
		if (texture != this.texture || idx >= maxIndex) {
			// apply the last texture
			flush();
			this.texture = texture;
		}
	}

	private void render() {
		if (texture != null)
			texture.bind();
		data.bind();
		data.draw(GL_TRIANGLES, 0, idx);
		data.unbind();
		renderCalls++;
	}

}
