const path = require('path');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');

module.exports = {
  entry: {
    opentfc: './src/main/frontend/src/index.ts'
  },
  output: {
    path: path.resolve(__dirname, 'src/main/resources/js'),
    filename: '[name].bundle.js',
    library: {
      name: 'OpenTFC',
      type: 'umd',
      export: 'default'
    },
    clean: false
  },
  resolve: {
    extensions: ['.ts', '.js', '.json']
  },
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: 'ts-loader',
        exclude: /node_modules/
      },
      {
        test: /\.css$/,
        use: [MiniCssExtractPlugin.loader, 'css-loader']
      }
    ]
  },
  plugins: [
    new MiniCssExtractPlugin({
      filename: '../css/[name].bundle.css'
    })
  ],
  performance: {
    hints: false
  }
};
