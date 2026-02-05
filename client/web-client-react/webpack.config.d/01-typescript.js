;(function(config) {
    const path = require('path');

    config.module.rules.push({
        test: /\.tsx?$/,
        use: {
            loader: 'ts-loader',
            options: {
                transpileOnly: true,
                compilerOptions: {
                    jsx: 'react-jsx',
                    module: 'esnext',
                    moduleResolution: 'node',
                    target: 'es2020',
                    strict: true,
                    esModuleInterop: true,
                    skipLibCheck: true
                }
            }
        },
        exclude: /node_modules/
    });

    config.resolve.extensions = config.resolve.extensions || ['.js', '.mjs'];
    config.resolve.extensions.push('.ts', '.tsx');

    config.resolve.modules = config.resolve.modules || [];
    config.resolve.modules.push(path.resolve(__dirname, '../../node_modules'));
    config.resolve.modules.push('node_modules');

    const reactEntryPath = path.resolve(__dirname, '../../../../client/web-client-react/build/processedResources/js/main/react/main.tsx');
    if (config.entry && config.entry.main) {
        config.entry.main.push(reactEntryPath);
    } else {
        config.entry = config.entry || {};
        config.entry.main = config.entry.main || [];
        config.entry.main.push(reactEntryPath);
    }
})(config);
