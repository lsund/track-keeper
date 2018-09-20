#!/bin/zsh

PORTS_FILE=~/.ports

CSS_DIR=resources/public/css/

echo "Downloading MUI CSS"

curl -X GET https://cdn.muicss.com/mui-0.9.39.zip -o mui.zip

echo "Installing mui in $CSS_DIR"

unzip mui.zip
cp mui-0.9.39/css/mui.css $CSS_DIR

echo "Cleaning up"

trash mui.zip mui-0.9.39

echo -e "\n\n\n"

projectname=$(echo $(basename $(pwd)))

echo "Renaming src/{clj,cljs}/template to $projectname"

mv src/clj/template src/clj/$projectname
mv src/cljs/template src/cljs/$projectname

echo "Changing git remote url"

sed -i -e "s/component-template.git/$projectname.git/" .git/config

echo "Changing port number to the lowest available"

highest_portnum=$(cat $PORTS_FILE | tail -1 | cut -d':' -f1)
new_portnum=$((highest_portnum + 1))

sed -i -e "s/:port 1337/:port $new_portnum/" resources/edn/config.edn
echo $new_portnum: $projectname >> $PORTS_FILE
