const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = (env, argv) => {
    const isProduction = argv.mode === 'production';

    return {
        entry: './src/jsMain/resources/react/main.tsx',
        output: {
            path: path.resolve(__dirname, 'dist'),
            filename: 'bundle.[contenthash].js',
            clean: true,
        },
        resolve: {
            extensions: ['.ts', '.tsx', '.js', '.mjs'],
            modules: [path.resolve(__dirname, 'node_modules'), 'node_modules'],
        },
        module: {
            rules: [
                {
                    test: /\.tsx?$/,
                    use: {
                        loader: 'ts-loader',
                        options: {
                            transpileOnly: true,
                        },
                    },
                    exclude: /node_modules/,
                },
                {
                    test: /\.css$/,
                    use: [
                        'style-loader',
                        'css-loader',
                        {
                            loader: 'postcss-loader',
                            options: {
                                postcssOptions: {
                                    plugins: [
                                        ['tailwindcss', { config: path.resolve(__dirname, 'tailwind.config.js') }],
                                        'autoprefixer',
                                    ],
                                },
                            },
                        },
                    ],
                },
                {
                    test: /\.m?js$/,
                    use: ['source-map-loader'],
                    enforce: 'pre',
                },
            ],
        },
        plugins: [
            new HtmlWebpackPlugin({
                template: path.resolve(__dirname, 'src/jsMain/resources/index.html'),
                filename: 'index.html',
            }),
        ],
        devtool: isProduction ? 'source-map' : 'eval-source-map',
        devServer: {
            historyApiFallback: true,
            hot: true,
            open: true,
            static: {
                directory: path.resolve(__dirname, 'src/jsMain/resources'),
            },
            watchFiles: ['src/jsMain/resources/react/**/*'],
            client: {
                overlay: {
                    errors: true,
                    warnings: false,
                },
            },
        },
        ignoreWarnings: [
            /Failed to parse source map/,
        ],
    };
};
