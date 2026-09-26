okay let's continue to spring-ai simple project

i want you to create a simple spring-ai project to 
get tool calling from orders table, i need you create 
new table for tool calling the table naming should be 
'report_orders'
id
user_id
created_by
created_at
updated_by
updated_at
order_status
total_amount
report_progress

So i want you create api to processing tool calling 
to calculate orders table total_price and calculate 
base 2 order status which COMPLETED and IN_PROGRESS 

do it in background process during hit the api 
report_progress should be IN_PROGRESS 
after finish during AI process then change it to COMPLETED 

then create one more api to listing all report_orders 
result.

For the api key you should using openrouter api key 
and save it to .env and don't expose it to git
(API key removed from this tracked file, it is stored in the git-ignored .env file)
please using this model:
deepseek/deepseek-v4-flash-0731
base url: https://openrouter.ai/api/v1

I think that's all for now. 
do your best, no mistakes. thx.


