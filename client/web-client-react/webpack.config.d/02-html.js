;(function(config) {
    const HtmlWebpackPlugin = require('html-webpack-plugin');
    const path = require('path');

    config.plugins.push(
        new HtmlWebpackPlugin({
            template: path.resolve(__dirname, '../../../../client/web-client-react/build/processedResources/js/main/index.html'),
            filename: 'index.html'
        })
    );
})(config);
