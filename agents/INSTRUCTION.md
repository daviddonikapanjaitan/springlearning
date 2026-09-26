Okay now please create new api for order 

Create table `orders`
id BIGINT
users_id relation to users
invoice_number text
item_name text
quantity BIGINT
item_price BIGINT
total_price BIGINT
order_description text
order_receive boolean
order_status text
created_by text
created_at date UTC+0
updated_by text
updated_at date UTC+0

please create api for create orders 
during this flow please make order_status PENDING
during hit api order_receive default false
and publish to kafka and create topics 
stream-order-users
and please create listener messages 
after order receive in kafka listener messages 
and please change order_receive to true and update updated_at 
and updated_by fields
and change order_status to IN_PROGRESS

and please create one api to update order_status to COMPLETED 
this api should need user_id and order_id as input and should check 
if the user_id is same as the user who created the order, 
if yes then update order_status to COMPLETED
when order is completed and 
also update updated_at and updated_by fields.

do it yourself, please no mistake. thx.
