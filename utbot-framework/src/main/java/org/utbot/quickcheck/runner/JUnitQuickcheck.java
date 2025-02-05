///*
// The MIT License
//
// Copyright (c) 2010-2021 Paul R. Holser, Jr.
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//*/
//
//package org.utbot.quickcheck.runner;
//
//import org.utbot.quickcheck.Property;
//import org.utbot.quickcheck.internal.GeometricDistribution;
//import org.utbot.quickcheck.internal.generator.GeneratorRepository;
//import org.utbot.quickcheck.internal.generator.ServiceLoaderGeneratorSource;
//import org.utbot.quickcheck.random.SourceOfRandomness;
//import org.utbot.quickcheck.runner.JUnitQuickcheckTestClass;
//import org.junit.Test;
//import org.junit.runners.BlockJUnit4ClassRunner;
//import org.junit.runners.model.FrameworkMethod;
//import org.junit.runners.model.InitializationError;
//import org.junit.runners.model.Statement;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//
///**
// * <p>JUnit test runner for junit-quickcheck property-based tests.</p>
// *
// * <p>When this runner runs a given test class, it regards only
// * {@code public} instance methods with a return type of {@code void} that are
// * marked with either the {@link Property}
// * annotation or the {@code org.junit.Test} annotation.</p>
// *
// * <p>This runner honors {@link org.junit.Rule}, {@link org.junit.Before},
// * {@link org.junit.After}, {@link org.junit.BeforeClass}, and
// * {@link org.junit.AfterClass}. Their execution is wrapped around the
// * verification of a property or execution of a test in the expected
// * order.</p>
// */
//public class JUnitQuickcheck extends BlockJUnit4ClassRunner {
//    private final GeneratorRepository repo;
//    private final GeometricDistribution distro;
//    private final Logger logger;
//
//    /**
//     * Invoked reflectively by JUnit.
//     *
//     * @param clazz class containing properties to verify
//     * @throws InitializationError if there is a problem with the
//     * properties class
//     */
//    public JUnitQuickcheck(Class<?> clazz) throws InitializationError {
//        super(new JUnitQuickcheckTestClass(clazz));
//
//        SourceOfRandomness random = new SourceOfRandomness(new Random());
//        repo =
//            new GeneratorRepository(random)
//                .register(new ServiceLoaderGeneratorSource());
//        distro = new GeometricDistribution();
//        logger = LoggerFactory.getLogger("junit-quickcheck.value-reporting");
//    }
//
//    @Override protected void validateTestMethods(List<Throwable> errors) {
//        validatePublicVoidNoArgMethods(Test.class, false, errors);
//        validatePropertyMethods(errors);
//    }
//
//    private void validatePropertyMethods(List<Throwable> errors) {
//        getTestClass().getAnnotatedMethods(Property.class)
//            .forEach(m -> m.validatePublicVoid(false, errors));
//    }
//
//    @Override protected List<FrameworkMethod> computeTestMethods() {
//        List<FrameworkMethod> methods = new ArrayList<>();
//        methods.addAll(getTestClass().getAnnotatedMethods(Test.class));
//        methods.addAll(getTestClass().getAnnotatedMethods(Property.class));
//        return methods;
//    }
//
//    @Override public Statement methodBlock(FrameworkMethod method) {
//        return method.getAnnotation(Test.class) != null
//            ? super.methodBlock(method)
//            : new org.utbot.quickcheck.runner.PropertyStatement(
//                method,
//                getTestClass(),
//                repo,
//                distro,
//                logger);
//    }
//}
