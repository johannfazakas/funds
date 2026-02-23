#!/bin/bash
awslocal s3 mb s3://imports

awslocal s3api put-bucket-cors --bucket imports --cors-configuration '{
  "CORSRules": [
    {
      "AllowedOrigins": ["*"],
      "AllowedMethods": ["GET", "PUT"],
      "AllowedHeaders": ["*"],
      "MaxAgeSeconds": 3600
    }
  ]
}'
