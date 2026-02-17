;(function(config) {
    const path = require('path');
    config.devServer = config.devServer || {};
    config.devServer.historyApiFallback = true;
    config.devServer.hot = true;
    config.devServer.watchFiles = [
        path.resolve(__dirname, '../../../../client/web-client-react/src/jsMain/resources/react/**/*')
    ];
})(config);
