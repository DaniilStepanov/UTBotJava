/*
 The MIT License

 Copyright (c) 2010-2021 Paul R. Holser, Jr.

 Permission is hereby granted, free of charge, to any person obtaining
 a copy of this software and associated documentation files (the
 "Software"), to deal in the Software without restriction, including
 without limitation the rights to use, copy, modify, merge, publish,
 distribute, sublicense, and/or sell copies of the Software, and to
 permit persons to whom the Software is furnished to do so, subject to
 the following conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package org.utbot.quickcheck.generator;

import org.utbot.quickcheck.generator.GeneratorConfiguration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Mark a parameter of a {@link org.utbot.quickcheck.Property}
 * method with this annotation to constrain the size of values generated for
 * the parameter.</p>
 *
 * <p>This annotation is recognized on array parameters and parameters of type
 * {@link java.util.Collection#size() Collection} and {@link
 * java.util.Map#size() Map}.</p>
 */
@Target({ PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE })
@Retention(RUNTIME)
@GeneratorConfiguration
public @interface Size {
    int min() default 0;

    int max();
}
