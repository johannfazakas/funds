;(function(config) {
    const path = require('path');
    const projectRoot = path.resolve(__dirname, '../../../../client/web-client-react');

    if (config.module && config.module.rules) {
        config.module.rules.forEach(function(rule) {
            if (rule.test && rule.test.toString().includes('css')) {
                const existingUse = rule.use || [];
                const newUse = [];
                existingUse.forEach(function(loader) {
                    newUse.push(loader);
                    const loaderName = typeof loader === 'string' ? loader : loader.loader;
                    if (loaderName && loaderName.includes('css-loader')) {
                        newUse.push({
                            loader: 'postcss-loader',
                            options: {
                                postcssOptions: {
                                    plugins: [
                                        ['tailwindcss', { config: path.join(projectRoot, 'tailwind.config.js') }],
                                        'autoprefixer',
                                    ],
                                },
                            },
                        });
                    }
                });
                rule.use = newUse;
            }
        });
    }
})(config);
